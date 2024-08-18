(ns tasks
  (:require [babashka.fs :as fs]
            [babashka.process :as proc]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [malli.core :as m]
            [malli.error :as me]
            [selmer.parser :as selmer]))

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
  [{:keys [src-suffix dst-suffixes tracked? check-modified verbose]} src-file]
  (if (not (tracked? src-file))
    (when verbose (println "Skipping" src-file "(not tracked)"))
    (do (when verbose (println "Checking" src-file))
        (let [stem (subs src-file 0 (- (count src-file) (count src-suffix)))
              modified (fs/last-modified-time src-file)
              dst-files (map #(str stem %) dst-suffixes)
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
  [& {:keys [dir src-suffix verbose] :as args}]
  (when verbose (println "Listing" (str dir)))
  (let [{dirs true files false} (group-by fs/directory? (fs/list-dir dir))
        results (->> files
                     (map str)
                     (filter #(str/ends-with? % src-suffix))
                     (sort)
                     (map (partial check-file args))
                     (remove nil?)
                     (vec))]
    (reduce into results (map #(check-dir (assoc args :dir %))
                              (sort dirs)))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn check
  {:org.babashka/cli {:coerce {:dst-suffixes [:str]
                               :check-modified :boolean
                               :tracked-only :boolean
                               :verbose :boolean}
                      :validate {:dir fs/directory?}}}
  [& {:keys [dir tracked-only verbose] :as args}]
  (when verbose (println "Running check with" (pr-str args)))
  (let [tracked (if tracked-only
                  (-> (proc/sh "git" "ls-files" dir) :out str/split-lines set)
                  (constantly true))]
    (when-let [errors (not-empty (check-dir :tracked? tracked args))]
      (doseq [err errors]
        (println "Error:" err))
      (println)
      (throw (ex-info "Errors found" {:errors errors})))))

(def categories-schema
  (m/schema
   [:map {:closed true}
    [:categories [:sequential
                  [:map {:closed true}
                   [:name string?]
                   [:description string?]]]]]))

(def category-schema
  (m/schema
   [:map {:closed true}
    [:fractals [:sequential
                [:map {:closed true}
                 [:file [:string]]
                 [:name [:string]]
                 [:description {:optional true} [:string]]
                 [:fractal-dimension {:optional true} [:string]]]]]]))

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

(defn read-fractal
  [{:keys [src-suffix dst-suffixes]}
   {:keys [file] :as info}]
  (assoc info
         :source (str file src-suffix)
         :images (into {} (map (fn [name suffix]
                                 (let [size (->> suffix
                                                 (re-find #".*_([0-9]+)[.][a-z]+$")
                                                 (second)
                                                 (parse-long))]
                                   [name {:size size
                                          :file (str file suffix)}]))
                               [:small :medium :large]
                               dst-suffixes))))

(defn read-category
  [{:keys [dir] :as args}
   {:keys [name] :as cat}]
  (let [info (read-yaml (str dir "/" name "/info.yaml") category-schema)
        info (update info :fractals (partial mapv (partial read-fractal args)))]
    (merge cat info)))

(defn read-info
  [& {:keys [dir] :as args}]
  (let [info (read-yaml (str dir "/info.yaml") categories-schema)
        info (update info :categories (partial mapv (partial read-category args)))]
    info))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn build-site
  [& {:keys [templates output template-args show-args] :as args}]
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
