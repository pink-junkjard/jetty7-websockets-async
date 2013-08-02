(ns com.keminglabs.jetty7-websockets-async.core
  (:require [clojure.core.async :refer [go chan close! <!! <! >!! >!]])
  (:import (org.eclipse.jetty.websocket WebSocket WebSocket$OnTextMessage WebSocketHandler)
           org.eclipse.jetty.server.handler.ContextHandler
           org.eclipse.jetty.server.handler.ContextHandlerCollection))

(defn handler
  [connection-chan send recv]
  (proxy [WebSocketHandler] []
    (doWebSocketConnect [request response]
      ;;TODO: turn this into a reify?
      (proxy [WebSocket WebSocket$OnTextMessage] []
        (onOpen [conn]
          (prn "connected!")
          (<!! connection-chan {:uri (.getRequestURI request)
                                :conn conn :send send :recv recv})
          (go (loop []
                (let [^String msg (<! send)]
                  (if (nil? msg)
                    (do
                        (close! recv)
                      (.close conn))
                    (do
                        (.sendMessage msg)
                      (recur)))))))
        (onMessage [msg]
          (>!! recv msg))
        (onClose [close-code msg]
          (prn ["closed" close-code msg])
          (close! send)
          (close! recv))))))

(defn configurator
  "Returns a Jetty configurator that configures server to listen for websocket connections and put request maps on `connection-chan`."
  ([connection-chan]
     (configurator connection-chan {}))
  ([connection-chan options]
     (fn [server]
       (let [ws-handler (handler connection-chan
                                 ;;TODO: change these to defaults that drop
                                 (:send options (chan))
                                 (:recv options (chan)))
             existing-handler (.getHandler server)
             contexts (doto (ContextHandlerCollection.)
                        (.setHandlers (into-array [(doto (ContextHandler.)
                                                     (.setContextPath "/")
                                                     (.setHandler existing-handler))
                                                   
                                                   (doto (ContextHandler.)
                                                     (.setContextPath "/") ;;TODO: make route(s) configurable
                                                     (.setHandler ws-handler))])))]
         (.setHandler server contexts)))))