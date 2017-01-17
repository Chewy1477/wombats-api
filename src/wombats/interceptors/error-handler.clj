(ns wombats.interceptors.error-handler
  (:require [io.pedestal.interceptor.error :refer [error-dispatch]]))


(defn- get-exception-data
  [exception]
  (-> exception
      ex-data
      :exception
      ex-data))

(defn- get-exception-type
  [exception]
  (-> exception
      get-exception-data
      :type))

(def service-error-handler
  (error-dispatch
   [context exception]

   [{:exception-type ExceptionInfo}]
   (condp = (get-exception-type exception)
     :invalid-schema (assoc context :response {:status 401 :body "Invalid schema"})
     (assoc context :io.pedestal.interceptor.chain/error exception))

   :else
   (do
     (clojure.pprint/pprint exception)
     (assoc context :io.pedestal.interceptor.chain/error exception))))
