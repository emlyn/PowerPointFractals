(ns tasks
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

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

(defn -check-file
  [{:keys [src-suffix dst-suffixes check-modified verbose]} src-file]
  (when verbose (println "Checking" src-file))
  (let [stem (subs src-file 0 (- (count src-file) (count src-suffix)))
        modified (fs/last-modified-time src-file)
        dst-files (map #(str stem %) dst-suffixes)
        missing (not-empty (filter (complement fs/exists?) dst-files))
        outdated (when check-modified
                   (not-empty (filter #(and (fs/exists? %)
                                            (pos? (compare modified (fs/last-modified-time %))))
                                      dst-files)))]
    (when (or missing outdated)
      (str "Source file " src-file
           (when missing (str " missing " (str/join ", " missing)))
           (when outdated (str " outdated " (str/join ", " outdated)))))))

(defn -check-dir
  [& {:keys [dir src-suffix verbose] :as args}]
  (when verbose (println "Listing" (str dir)))
  (let [{dirs true files false} (group-by fs/directory? (fs/list-dir dir))
        results (->> files
                     (map str)
                     (filter #(str/ends-with? % src-suffix))
                     (sort)
                     (map (partial -check-file args))
                     (remove nil?)
                     (vec))]
    (reduce into results (map #(-check-dir (assoc args :dir %)) (sort dirs)))))

(defn check
  {:org.babashka/cli {:coerce {:dst-suffixes [:str]
                               :check-modified :boolean
                               :verbose :boolean}
                      :validate {:dir fs/directory?}}}
  [& {:keys [verbose] :as args}]
  (when verbose (println "Running check with" (pr-str args)))
  (when-let [errors (not-empty (-check-dir args))]
    (doseq [err errors]
      (println "Error:" err))
    (println)
    (throw (ex-info "Errors found" {:errors errors}))))

(defn build-site
  [args]
  (prn args))
