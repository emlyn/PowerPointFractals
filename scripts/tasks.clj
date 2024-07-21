(ns tasks
  (:require [babashka.fs :as fs]
            [babashka.process :as proc]
            [clojure.string :as str]))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn install-pre-commit
  [& {}]
  (let [src "scripts/pre-commit"
        dst ".git/hooks/pre-commit"]
    (when-not (fs/exists? src)
      (throw (ex-info "Pre-commit hook not found" {:location src})))
    (if (fs/exists? dst)
      (if (and (= (fs/size src) (fs/size dst))
               (= (slurp src) (slurp dst)))
        (println "Pre-commit hook already installed")
        (throw (ex-info "Pre-commit hook already exists" {:location dst})))
      (do
        (fs/copy src dst)
        (println "Pre-commit hook installed")))))

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
    (reduce into results (map #(check-dir (assoc args :dir %)) (sort dirs)))))

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

(defn build-site
  [args]
  (prn args))
