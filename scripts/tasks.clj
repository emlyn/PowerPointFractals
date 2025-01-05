;; Copyright (c) 2024 Emlyn Corrin.
;; This work is licensed under the terms of the MIT license.
;; For a copy, see <https://opensource.org/license/MIT>.

(ns tasks
  (:require [babashka.fs :as fs]
            [babashka.process :as proc]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [malli.core :as m]
            [malli.error :as me]
            [selmer.parser :as selmer]
            [selmer.filters :as filters]
            [infix.core :as infix]
            [infix.macros :refer [from-string]]))

(def INFO_FILE "_info.yaml")
(def SRC_SUFFIX ".pptx")
(def DST_SUFFIXES {:small "_600.png"
                   :medium "_1200.png"
                   :large "_2400.png"})

(defn- num-str?
  [s]
  (and (string? s)
       (re-matches #"[0-9]+(?:\.[0-9]*)?" s)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn install-pre-commit
  [& {:keys [overwrite]}]
  (let [src "scripts/pre-commit"
        dst ".git/hooks/pre-commit"]
    (cond
      (not (fs/exists? src))
      (throw (ex-info "Pre-commit hook not found" {:location src}))

      (not (fs/exists? dst))
      (do
        (fs/copy src dst)
        (println "Pre-commit hook installed"))

      (and (= (fs/size src) (fs/size dst))
           (= (slurp src) (slurp dst)))
      (println "Pre-commit hook already installed")

      overwrite
      (do
        (fs/copy src dst {:replace-existing true})
        (println "Pre-commit hook installed over old version"))

      :else
      (throw (ex-info "Pre-commit hook already exists, use --overwrite to replace it" {:location dst})))))

(defn- check-file
  [{:keys [tracked? check-modified verbose]} src-file]
  (if (not (tracked? src-file))
    (when verbose (println "Skipping" src-file "(not tracked)"))
    (do (when verbose (println "Checking" src-file))
        (let [stem (subs src-file 0 (- (count src-file) (count SRC_SUFFIX)))
              modified (fs/last-modified-time src-file)
              dst-files (map #(str stem %) (vals DST_SUFFIXES))
              missing (not-empty (filter (complement fs/exists?) dst-files))
              outdated (when check-modified
                         (not-empty (filter #(and (fs/exists? %)
                                                  (pos? (compare modified (fs/last-modified-time %))))
                                            dst-files)))
              untracked (not-empty (remove tracked? dst-files))]
          (when (or missing outdated untracked)
            (str "Source file " src-file
                 (when missing (str " missing " (str/join ", " missing)))
                 (when outdated (str " outdated " (str/join ", " outdated)))
                 (when untracked (str " untracked " (str/join ", " untracked)))))))))

(defn- check-dir
  [& {:keys [dir verbose] :as args}]
  (when verbose (println "Listing" (str dir)))
  (let [{dirs true files false} (group-by fs/directory? (fs/list-dir dir))
        results (->> files
                     (map str)
                     (filter #(str/ends-with? % SRC_SUFFIX))
                     (sort)
                     (map (partial check-file args))
                     (remove nil?)
                     (vec))]
    (reduce into results (map #(check-dir (assoc args :dir %))
                              (sort dirs)))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn check
  {:org.babashka/cli {:coerce {:check-modified :boolean
                               :tracked-only :boolean
                               :verbose :boolean}
                      :validate {:dir fs/directory?}}}
  [& {:keys [dir tracked-only verbose] :as args}]
  (when verbose (println "Running check with" (pr-str args)))
  (let [tracked (if tracked-only
                  (-> (proc/sh "git" "ls-files" dir) proc/check :out str/split-lines set)
                  (constantly true))]
    (when-let [errors (not-empty (check-dir :tracked? tracked args))]
      (doseq [err errors]
        (println "Error:" err))
      (println)
      (throw (ex-info "Errors found" {:errors errors})))))

(def categories-schema
  (m/schema
   [:map {:closed true}
    [:tag-groups [:sequential
            [:map {:closed true}
             [:group [:or [:string] [:nil]]]
             [:description [:string]]
             [:tags [:sequential
                     [:map {:closed true}
                      [:name [:string]]
                      [:description [:string]]]]]]]]
    [:categories [:sequential
                  [:map {:closed true}
                   [:name [:string]]
                   [:description [:string]]]]]]))

(def category-schema
  (m/schema
   [:map {:closed true}
    [:fractals [:sequential
                [:map {:closed true}
                 [:file [:string]]
                 [:name [:string]]
                 [:author {:optional true} [:string]]
                 [:year {:optional true} [:int]]
                 [:media {:optional true} [:string]]
                 [:description {:optional true} [:string]]
                 [:dimension {:optional true} [:or [:string] [:int] [:double]]]
                 [:tags {:optional false} [:sequential [:string]]]
                 [:links {:optional true} [:sequential
                                           [:map {:closed true}
                                            [:name [:string]]
                                            [:url [:string]]]]]]]]]))

(defn read-yaml
  [fname schema]
  (let [data (slurp fname)
        info (yaml/parse-string data)]
    (if (m/validate schema info)
      info
      (do
        (println "Error in" fname)
        (-> (m/explain schema info)
            (me/humanize)
            (pprint/pprint))
        (System/exit 1)))))

(defn eval-str
  [s]
  (if (number? s)
    s
    (let [f (from-string []
                         (merge infix/base-env
                                {:phi (/ (inc (Math/sqrt 5)) 2)})
                         s)]
      (f))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn eval-string
  [& {}]
  (let [expr (str/join " " *command-line-args*)]
    (println "Evaluating:" expr)
    (println "Result:" (eval-str expr))))

(defn- fixup-expression
  "Take an expression valid for infix/from-string and make it valid for MathJax.
   Currently this will replace:
   - 'root(a, b)' with 'root(a)(b)'."
  [expr]
  (str/replace (str expr) #"\broot\(([0-9.]+),\s*" "root($1)("))

(defn- img-dimensions
  [fname]
  (let [o (-> (proc/sh "file" "-b" fname)
              proc/check
              :out)
        m (re-find #", ([0-9]+) ?x ?([0-9]+)," o)]
    (if m
      (mapv parse-long (next m))
      (throw (ex-info "Failed to get image dimensions" {:file fname
                                                        :out o})))))


(defn read-fractal
  [{:keys [dir category]}
   {:keys [file dimension author year media] :as info}]
  (let [imgs (reduce-kv (fn [m name suffix]
                          (let [[w h] (img-dimensions (str dir "/" category "/" file suffix))]
                            (assoc m name {:width w
                                           :height h
                                           :file (str file suffix)})))
                        {}
                        DST_SUFFIXES)]
    (assoc info
           :source (str file SRC_SUFFIX)
           :images imgs
           :aspect (let [{:keys [width height]} (->> imgs vals (sort-by :width) last)]
                     (/ width height))
           :category category
           :author (or author "Emlyn Corrin")
           :year (or year 2024)
           :media (or media "Digital media (Microsoft PowerPoint)")
           :dimension (fixup-expression dimension)
           :dimension_val (when (and (string? dimension)
                                     (not (num-str? dimension)))
                            (eval-str dimension)))))

(defn read-category
  [{:keys [dir] :as args}
   {:keys [name] :as cat}]
  (let [info (read-yaml (str dir "/" name "/" INFO_FILE) category-schema)
        info (update info :fractals (partial mapv (partial read-fractal (assoc args :category name))))]
    (merge cat info)))

(defn check-info
  [info]
  (let [nils (->> info
                  :tag-groups
                  (filter (comp nil? :group))
                  count)
        tags (->> info
                  :tag-groups
                  (mapcat :tags)
                  (map :name)
                  frequencies)
        files (->> info
                   :fractals
                   (map #(select-keys % [:file :category :name]))
                   (group-by :file)
                   (filter #(> (count (val %)) 1))
                   seq)]
    (when (> nils 1)
      (throw (ex-info "There should only be one unnamed tag group" {:num nils})))
    (when-let [dup-tags (->> tags
                             (filter #(> (second %) 1))
                             seq)]
      (throw (ex-info "Duplicate tags" {:tags dup-tags})))
    (doseq [frac (:fractals info)
            :let [bad-tags (seq (remove tags (:tags frac)))]
            :when bad-tags]
      (throw (ex-info "Fractal has unknown tags" (assoc (select-keys frac [:name :file :category :tags])
                                                        :bad-tags bad-tags))))
    (when files
      (throw (ex-info "Duplicate filenames" {:files files}))))
  info)

(defn read-info
  [& {:keys [dir] :as args}]
  (as-> dir $
    (str $ "/" INFO_FILE)
    (read-yaml $ categories-schema)
    (update $ :categories (partial mapv (partial read-category args)))
    (assoc $ :fractals (mapv (fn [fract id]
                               (assoc fract :id id))
                             (mapcat :fractals (:categories $))
                             (range)))
    (update $ :categories (partial mapv #(assoc % :fractals
                                                (filter (comp (partial = (:name %)) :category)
                                                        (:fractals $)))))
    (check-info $)))

(defn num?
  [v]
  (or (number? v) (num-str? v)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn build-site
  [& {:keys [templates output template-args show-args] :as args}]
  (filters/add-filter! :isnum num?)
  (filters/add-filter! :trimnum (fn [val & [places]]
                                  (if (num? val)
                                    (str/replace (format (format "%%.%df" (if places (parse-long places) 0))
                                                         (if (string? val)
                                                           (parse-double val)
                                                           (double val)))
                                                 #"\.?0*$" "")
                                    val)))
  (println "Building template args")
  (let [template-args
        (merge (read-info args)
               template-args)]
    (if show-args
      (do (println "Args:")
          (pprint/pprint template-args))
      (fs/walk-file-tree templates
                         {:visit-file
                          (fn [f _]
                          ;; Selmer expects a path relative to the resources
                            (let [rel (str (fs/relativize templates f))
                                  out (str output fs/file-separator rel)]
                              (println "Processing" (str f) "->" out)
                              (spit out (selmer/render-file rel template-args))
                              :continue))}))))

(defn parse-sizes
  [sizes]
  (reduce (fn [m s]
            (if-let [[_ n v] (re-find #"(?:([\d]+)x)?([\d.]+)" s)]
              (update m (parse-double v)
                      #(+ (or % 0) (if n (parse-long n) 1)))
              (throw (ex-info "Invalid size" {:size s}))))
          {}
          sizes))

(defn solve
  [func xmin xmax]
  (loop [xmin xmin
         xmax xmax
         fmin (func xmin)
         fmax (func xmax)]
    (let [x (/ (+ xmin xmax) 2.0)
          f (func x)]
      (cond
        (or (zero? f) (= x xmin) (= x xmax))   [x f]
        (= (Math/signum f) (Math/signum fmin)) (recur x xmax f fmax)
        (= (Math/signum f) (Math/signum fmax)) (recur xmin x fmin f)
        :else
        (throw (ex-info "Failed to find root" {:xmin xmin :fmin fmin
                                               :xmax xmax :fmax fmax
                                               :x x :f f}))))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn calc-dimension
  {:org.babashka/cli {:coerce {:size :double
                               :sizes [:string]}
                      :alias {:s :sizes}}}
  [& {:keys [size sizes dmin dmax]}]
  (let [intify (fn [v] (if (== v (int v)) (int v) v))
        smap (parse-sizes sizes)]
    (println (format "Full size: %s" (intify size)))
    (doseq [[v n] (reverse (sort smap))]
      (println (format "Zoom size: %s (scale 1/%s), count: %s" (intify v) (intify (/ size v)) n)))
    (println)
    (if (= 1 (count smap))
      (let [[v n] (first smap)
            r (intify (/ size v))
            d (/ (Math/log n) (Math/log r))]
        (println (format "Dimension: log(%s) / log(%s) = %s" n r d)))
      (let [func (fn [d]
                   (reduce-kv (fn [acc v n]
                                (+ acc (* n (Math/pow (/ v size) d))))
                              -1
                              smap))
            [d res] (solve func dmin dmax)]
        (println (format "Dimension: %s (residual %s)" d res))))))
