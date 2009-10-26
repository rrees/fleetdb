(ns fleetdb.core
  (use (fleetdb util)))

(defn init []
  {:rmap (sorted-map)
   :imap (sorted-map)})

(defn- index-assoc [index on record]
  (if-let [val (on record)]
    (assoc index val (:id record))))

(defn- q-insert [db {:keys [record]}]
  (assert record)
  (let [id   (:id record)
        rmap (:rmap db)
        imap (:imap db)]
    (assert id)
    (assert (not (contains? rmap id)))
    (let [rmap-new (assoc rmap id record)
          imap-new (reduce
                     (fn [imap-int [on index]]
                       (assoc imap-int on (index-assoc index on record)))
                     imap imap)]
      (assoc db :rmap rmap-new :imap imap-new))))

(def- conj-op?
  #{:and :or})

(def- conj-op-fns
  {:and and? :or or?})

(def- sing-op?
  #{:= :!= :< :> :<= :> :>=})

(def- sing-op-fns
  {:= = :!= != :< < :<= <= :> > :>= >=})

(def- doub-op?
  #{:>< :>=< :><= :>=<=})

(def- doub-op-fns
  {:>< [> <] :>=< [>= <] :><= [> <=] :>=<= [>= <=]})

(defn- where-pred [[op & wrest]]
  (cond
    (conj-op? op)
      (let [subpreds (map #(where-pred %) wrest)
            conj-op-fn  (conj-op-fns op)]
        (fn [record]
          (conj-op-fn (map #(% record) subpreds))))
    (sing-op? op)
      (let [[attr val]  wrest
            sing-op-fn (sing-op-fns op)]
        (fn [record]
          (sing-op-fn (attr record) val)))
    (doub-op? op)
      (let [[attr [val1 val2]] wrest
            [doub-op-fn1 doub-op-fn2]      (doub-op-fns op)]
        (fn [record]
          (let [record-val (attr record)]
            (and (doub-op-fn1 record-val val1)
                 (doub-op-fn2 record-val val2)))))
    (= op :in)
      (let [[attr val-vec] wrest
            val-set        (set val-vec)]
        (fn [record]
          (contains? val-set (attr record))))
    (nil? op)
      (constantly true)
    :else
      (raise "where op not recognized")))

(defn- apply-offset [records offset]
  (if offset (drop offset records) records))

(defn- apply-limit [records limit]
  (if limit (take limit records) records))

(defn- find-records [records {:keys [where offset limit]}]
  (-> (filter (where-pred where) records)
    (apply-offset offset)
    (apply-limit limit)))

(defn- apply-only [records only]
  (if only
    (map #(select-keys % only) records))
    records)

(defn- q-select [db {:keys [only] :as opts}]
  (-> (find-records (vals (:rmap db)) opts)
    (apply-only only)))

(defn- q-count [db opts]
  (count (find-records (vals (:rmap db)) opts)))

(defn- q-update [db {:keys [with] :as opts}]
  (assert with)
  (let [old-rmap   (:rmap db)
        up-records (find-records (vals old-rmap) opts)
        new-rmap
          (reduce
            (fn [int-rmap up-record]
              (assoc int-rmap (:id up-record)
                (merge-compact up-record with)))
            old-rmap
            up-records)]
    (assoc db :rmap new-rmap)))

(defn- q-delete [db opts]
  (let [old-rmap    (:rmap db)
        del-records (find-records (vals old-rmap) opts)
        new-rmap
          (reduce
            (fn [int-rmap del-record]
              (dissoc int-rmap (:id del-record)))
            old-rmap
            del-records)]
    (assoc db :rmap new-rmap)))

(defn- q-index [db {:keys [on where]}]
  (assoc-in db [:imap on]
    (reduce
      (fn [int-index record]
        (if-let [val (on record)]
          (assoc int-index val (:id record))
          int-index))
      (sorted-map)
      (vals (:rmap db)))))

(def- query-fns
  {:select q-select
   :count  q-count
   :insert q-insert
   :update q-update
   :delete q-delete
   :index  q-index})

(defn exec [db [query-type opts]]
  (if-let [queryfn (query-fns query-type)]
    (queryfn db opts)
    (raise "command not recognized")))
