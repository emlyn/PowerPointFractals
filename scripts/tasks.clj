(ns tasks
  (:require [babashka.fs :as fs]
            [babashka.process :as proc]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
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

(defn- image-info
  [[fname size_str]]
  (let [size (parse-long size_str)]
    {:name fname
     :size size}))

(defn- list-file
  [& {:keys [dir category stem src-suffix]}]
  (let [images (fs/glob (str dir "/" category "/") (str stem "_*.png"))
        images (remove nil? (map #(re-find #".*_([0-9]+)[.]png$" (fs/file-name %)) images))]
    (when-not (= 3 (count images)) (throw (ex-info "Not 3 images" {:num (count images) :images images})))
    {:name stem
     :category category
     :source (str stem src-suffix)
     :images (into {} (map vector
                       [:small :medium :large]
                       (sort-by :size (map image-info images))))}))

(defn- list-category
  [& {:keys [dir category src-suffix] :as args}]
  (let [files (->> (fs/list-dir (str dir "/" category))
                   (map fs/file-name)
                   (filter #(str/ends-with? % src-suffix))
                   (map #(subs % 0 (- (count %) (count src-suffix))))
                   (sort)
                   (vec))]
    (map #(list-file :stem % args) files)))

(defn- list-categories
  [& {:keys [dir] :as args}]
  (->> (fs/list-dir dir)
       (filter fs/directory?)
       (map fs/file-name)
       (sort)
       (map (fn [c]
              (let [[_ name] (str/split c #"-" 2)]
                {:name name
                 :pictures (list-category :category c args)})))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn build-site
  [& {:keys [templates output raw-root show-args] :as args}]
  (let [template-args
        {:raw-root raw-root
         :categories (list-categories args)}]
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
