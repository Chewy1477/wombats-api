(ns wombats.handlers.auth
  (:require [clojure.string :refer [ends-with? join split]]
            [io.pedestal.interceptor.helpers :as interceptor]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [buddy.core.mac :as mac]
            [buddy.core.codecs :as codecs]
            [wombats.interceptors.github :refer [get-github-settings]]
            [wombats.interceptors.authorization :refer [get-api-uri get-hashing-secret]]
            [wombats.daos.helpers :as dao]
            [wombats.constants :refer [github-access-token-url
                                       github-user-profile-url
                                       github-authorize-url
                                       github-scopes]]))

;; Helper Functions

(defn- get-access-token
  "POST request to get a GitHub OAuth token.
  If no auth token is found, nil will return"
  [query-params]
  (http/post github-access-token-url
             {:query-params query-params
              :headers {"Accept" "application/json"}}
             (fn [{:keys [body]}]
               (get (parse-string body true) :access_token nil))))

(defn- get-github-user
  "Fetches the github user attached to a given access token"
  [access-token]
  (http/get github-user-profile-url
            {:headers {"Authorization" (str "token " access-token)
                       "Accept" "application/json"}}))

(defn- remove-slash
  "Removes the trailing slash from a string (if it exists)"
  [string]
  (if (and string
           (ends-with? string "/"))
    (-> string
        (split #"")
        (drop-last)
        (join))
    string))

(defn- get-referer
  "Pulls out the referer from a request object"
  [request]
  (get-in request [:headers "referer"]))

(defn- get-formatted-referer
  "Formats the referer"
  [request]
  (remove-slash (get-referer request)))

(defn- parse-user-response
  "Parses the body if the request succeeded"
  [{:keys [body status]}]
  (when (= status 200)
    (parse-string body true)))

(defn- redirect-home
  "Final redirect home with or without access token appended"
  ([context redirect]
   (assoc context :response (assoc (:response context)
                                   :headers {"Location" redirect}
                                   :status 302)))
  ([context redirect access-token]
   (assoc context :response (assoc (:response context)
                                   :headers {"Location" (str redirect "?access-token=" access-token)}
                                   :status 302))))

;; Handlers

(def ^:swagger-spec signin-spec
  {"/api/v1/auth/github/signin"
   {:get {:description "Gets authorization and access token"
          :tags ["auth"]
          :operationId "signout"
          :response {:302 {:description "signout response"}}}}})

;; TODO signing secret should not be consistant across requests. Gen uuid to send.
(def signin
  "Signin handler"
  (interceptor/before
   ::sign-in
   (fn [{:keys [response request] :as context}]
     (let [{:keys [client-id signing-secret]} (get-github-settings context)
           api-uri (get-api-uri context)
           github-redirect (str github-authorize-url
                                "?client_id=" client-id
                                "&scope=" github-scopes
                                "&state=" signing-secret
                                "&redirect_uri=" (str api-uri
                                                      "/api/v1/auth/github/callback?referer="
                                                      (get-formatted-referer request)))]

       (assoc context :response (assoc response
                                       :headers {"Location" github-redirect}
                                       :status 302
                                       :body nil))))))

(defn- gen-user-access-token
  "Generates an access token to be used by the wombats client"
  [secret-key github-id]
  (-> (str github-id)
      (mac/hash {:key secret-key :alg :hmac+sha256})
      (codecs/bytes->hex)))

(def github-callback
  "Callback handler from GitHub OAuth request"
  (interceptor/before
   ::github-callback
   (fn [{:keys [request response] :as context}]
     (let [{:keys [client-id
                   client-secret
                   signing-secret]} (get-github-settings context)
           {:keys [code referer state]} (:query-params request)
           failed-callback (redirect-home context referer)]

       (if (= state signing-secret)
         (let [github-access-token @(get-access-token {:client_id client-id
                                                       :client_secret client-secret
                                                       :code code})
               user (when github-access-token
                      (parse-user-response @(get-github-user github-access-token)))
               create-or-update-user (dao/get-fn :create-or-update-user context)
               get-user-by-github-id (dao/get-fn :get-user-by-github-id context)]
           (if (and github-access-token user)
             (let [user-update (select-keys user [:login :id :avatar_url])
                   user-access-token (gen-user-access-token (get-hashing-secret context)
                                                            (:id user-update))
                   current-user (get-user-by-github-id (:id user-update))
                   updated-user @(create-or-update-user user-update
                                                        github-access-token
                                                        user-access-token
                                                        (:user/id current-user))]
               (redirect-home context referer user-access-token))
             failed-callback))
         failed-callback)))))

(def ^:swagger-spec signout-spec
  {"/api/v1/auth/github/signout"
   {:get {:description "Removes auth token from db and redirects to home."
          :tags ["auth"]
          :operationId "signout"
          :response {:302 {:description "signout response"}}}}})

(def signout
  "Signout handler"
  (interceptor/before
   ::signout
   (fn [{:keys [request response] :as context}]
     (let [access-token (get-in request [:headers "authorization"])
           remove-access-token (dao/get-fn :remove-access-token context)
           web-client-redirect (get-formatted-referer request)]

       (when access-token
         @(remove-access-token access-token))

       (assoc context :response (assoc response
                                       :headers {"Location" web-client-redirect}
                                       :status 302
                                       :body nil))))))
