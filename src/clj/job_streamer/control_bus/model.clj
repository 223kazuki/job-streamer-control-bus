(ns job-streamer.control-bus.model
  (:use [datomic-schema.schema :only [defpart defschema fields part schema]])
  (:require [datomic.api :as d]
            [datomic-schema.schema :as s]))

(def uri "datomic:mem://job-streamer")
(defonce conn (atom nil))

(defn query [q & params]
  (let [db (d/db @conn)]
    (apply d/q q db params)))

(defn pull [pattern eid]
  (let [db (d/db @conn)]
    (d/pull db pattern eid)))

(defn transact [transaction]
  @(d/transact @conn transaction))

(defn dbparts []
  [(part "job")])

(defn dbschema []
  [(schema job
           (fields
            [id :string  :indexed :unique-value :fulltext]
            [restartable :boolean]
            [property :ref :many]
            [step :ref :many]
            [edn-notation :string]
            [executions :ref :many]))
   (schema property
           (fields
            [name  :string]
            [value :string]))
   (schema step
           (fields
            [id :string :indexed]
            [start-limit :long]
            [allow-start-if-complete :boolean]
            [chunk :ref]
            [batchlet :ref]))
   (schema chunk
           (fields
            [checkpoint-policy :enum [:item :custom]]
            [item-count  :long]
            [time-limit  :long]
            [skip-limit  :long]
            [retry-limit :long]))
   (schema batchlet
           (fields
            [ref :string]))
   (schema agent
           (fields
            [instance-id :uuid :unique-value :indexed]
            [name :string]))
   (schema job-execution
           (fields
            [start-time  :instant]
            [end-time    :instant]
            [job-parameters :string]
            [batch-status :ref]
            [agent :ref]
            [execution-id :long]
            [step-executions :ref :many]))
   (schema step-execution
           (fields
            [step :ref]
            [step-execution-id :long]
            [start-time :instant]
            [end-time   :instant]
            [batch-status :ref]
            [execution-exception :string]))
   (schema execution-log
           (fields
            [date :instant]
            [agent :ref]
            [step-execution-id :long]
            [logger :string]
            [level :ref]
            [message :string :fulltext :indexed]
            [exception :string]))
   (schema schedule
           (fields
            [job :ref]
            [cron-notation :string]))])

(defn generate-enums [tempid-fn & enums]
  (apply concat
         (map #(s/get-enums tempid-fn (name (first %)) :db.part/user (second %)) enums)))

(defn create-schema []
  (d/create-database uri)
  (reset! conn (d/connect uri))
  (let [schema (concat
                (s/generate-parts d/tempid (dbparts))
                (generate-enums d/tempid
                                [:batch-status [:registered :abandoned :completed :failed :started :starting :stopped :stopping :unknown]]
                                [:log-level [:trace :debug :info :warn :error]])
                (s/generate-schema d/tempid (dbschema)))]
    (d/transact
     @conn
     schema)))
