(ns outdated
  (:require [clojure.edn :as edn]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

;; Inspired by an idea from @seancorfield on Clojurians Slack

(defn with-release [deps]
  (zipmap (keys deps)
          (map #(assoc % :mvn/version "RELEASE")
               (vals deps))))

(defn deps->versions [deps]
  (let [res (sh "clojure" "-Sdeps" (str {:deps deps}) "-Stree")
        tree (:out res)
        lines (str/split tree #"\n")
        top-level (remove #(str/starts-with? % " ") lines)
        deps (map #(str/split % #" ") top-level)]
    (reduce (fn [acc [dep version]]
              (assoc acc dep version))
            {}
            deps)))

(defn check-outdated
  [& {:keys [deps-file]}]
  (let [deps (-> deps-file slurp edn/read-string :deps)
        version-map (deps->versions deps)
        new-version-map (deps->versions (with-release deps))]
    (doseq [[dep version] version-map
            :let [new-version (get new-version-map dep)]
            :when (not= version new-version)]
      (println dep "can be upgraded from" version "to" new-version))))
