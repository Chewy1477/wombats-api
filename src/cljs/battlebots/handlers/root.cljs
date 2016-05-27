(ns battlebots.handlers.root
    (:require [re-frame.core :as re-frame]

              ;; Handlers
              [battlebots.handlers.account]
              [battlebots.handlers.games]
              [battlebots.handlers.routing]
              [battlebots.handlers.ui]

              [battlebots.db :as db]
              [battlebots.services.battlebots :refer [get-games
                                                      get-current-user]]))

(defn initialize-app-state
  "initializes application state on bootstrap"
  [_ _]
  db/default-db)

(defn bootstrap
  "makes all necessary requests to initially bootstrap an application"
  [db _]

  (get-current-user
    #(re-frame/dispatch [:update-user %])
    #(re-frame/dispatch [:update-errors %]))

  (get-games
    #(re-frame/dispatch [:update-games %])
    #(re-frame/dispatch [:update-errors %]))

  (assoc db :bootstrapping? true))

(re-frame/register-handler :initialize-app initialize-app-state)
(re-frame/register-handler :bootstrap-app bootstrap)
