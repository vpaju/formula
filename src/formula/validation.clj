(ns formula.validation)

(def call
  "Changes strings into function calls"
  #(resolve (symbol (name %))))

(defn message
  "Creates messages"
  [k custom message]
  (clojure.string/trim (or (when custom
                             (format custom (name k)))
                           (format message (name k)))))

(defn present
  "Checks if key is present"
  [field-key vali-map & [messages]]
  (let [custom (:present (field-key messages))]
    (when (false? (get vali-map field-key false))
      {field-key (message field-key custom "%s must be present")})))

(defn allow-nil
  "Checks to see if value is nil"
  [field-key answer vali-map & [messages]]
  (let [custom (:allow-nil (field-key messages))
        value (field-key vali-map)
        match (when-not answer (nil? value))]
    (when match
      {field-key (message field-key custom "%s can't be empty")})))

(defn allow-blank
  "Checks to see if value is blank"
  [field-key answer vali-map & [messages]]
  (let [custom (:allow-blank (field-key messages))
        value (field-key vali-map)
        value (if (string? value) (count (clojure.string/trim value)) value)
        value (if (nil? value) 1 value)
        match (when-not answer
                (= 0 value))]
    (when match
      {field-key (message field-key custom "%s can't be blank")})))

(defn confirm
  "Checks to see if values are the same"
  [field-key confirms vali-map & [messages]]
  (let [value (field-key vali-map)
        custom (:confirm (field-key messages))
        no-match (seq (remove #(= value (% vali-map)) confirms))]
    (when no-match
      {field-key (message field-key custom
                          (str "%s must match " (name (first no-match))))})))

(defn exclusion
  "Checks to see if value is not in coll"
  [field-key exclusions vali-map & [messages]]
  (let [value (field-key vali-map)
        custom (:exclusion (field-key messages))
        match (some #{value} exclusions)]
    (when match
      {field-key (message field-key custom
                          "%s is not an acceptable term")})))

(defn inclusion
  "Checks to see if value is in coll"
  [field-key inclusions vali-map & [messages]]
  (let [value (field-key vali-map)
        custom (:inclusion (field-key messages))
        match (some #{value} inclusions)]
    (when-not match
      {field-key (message field-key custom
                          "%s is not an acceptable term")})))

(defn formats
  "Checks to see if value is in correct format"
  [field-key regex vali-map & [messages]]
  (let [value (field-key vali-map)
        custom (:formats (field-key messages))
        match (re-find regex value)]
    (when-not match
      {field-key (message field-key custom
                          "%s is not the correct format")})))

(defn length
  "Checks to see if value is the correct length"
  [field-key length-map vali-map & [messages]]
  (let [min (length-map :min)
        max (length-map :max)
        both-values (not-any? nil? [min max])
        both-empty (and (nil? max) (nil? min))
        value (count (field-key vali-map))
        custom (:length (field-key messages))
        result (cond
                both-empty nil
                both-values (when-not (some #{value}
                                            (range min (+ 1 max)))
                              (str "%s should be between "
                                   min " and " max
                                   " characters"))
                (nil? max) (when-not (>= value min)
                             (str "%s should be at least "
                                  min " characters"))
                (nil? min) (when-not (<= value max)
                             (str "%s should be at most "
                                  max " characters")))]
    (when result
      {field-key (message field-key custom result)})))

(defn unique
  "Checks to see if value is unique.  check-fn should be a function
   that takes the value as an argument.  It should return true if not
   unique. e.g. record in database will be true"
  [field-key check-fn vali-map & [messages]]
  (let [value (field-key vali-map)
        result (check-fn value)
        custom (:unique (field-key messages))]
    (when result
      {field-key (message field-key custom "%s must be unique")})))

(defn sender-loop [field rules vali-map & [messages]]
  (let [vali (fn [r & [msg]] (if (map? r)
                      ((call (apply key r)) field (apply val r) vali-map msg)
                      ((call r) field vali-map msg)))]
        (loop [rules rules errors {}]
          (if (or (empty? rules) (seq errors))
            errors
            (recur (rest rules)
                   (conj errors (vali (first rules) messages)))))))

(defn default-check
  "Checks to see if value is nil or blank."
  [field rules vali-map & [messages]]
  (let [allow-nil (some #{:allow-nil} rules)
        allow-blank (some #{:allow-blank} rules)
        present (first (filter #{:present} rules))
        rules [{:allow-nil allow-nil}
               {:allow-blank allow-blank}]
        rules (if present (cons present rules) rules)]
    (sender-loop field rules vali-map messages)))

(defn sender 
  "Sends rules to correct functions. Once an validation error has
   occurred for a specific field, then the rest of the rules are
   ignored."
  [field rules vali-map & [messages]]
  (let [check (default-check field rules vali-map messages)
        no-errors (empty? check)
        rules (when no-errors
                (remove #{:allow-nil :allow-blank :present} rules))]
    (if no-errors
      (sender-loop field rules vali-map messages)
      check)))

(defn validate 
  "Takes rules vector, to be validated map, and custom messages (optional).
   Each field's rules are ran until an validation error occurs."
  [rule-vec vali-map & [messages]]
  (let [errors (for [[field & rules] rule-vec
                     :let [result (sender field rules vali-map messages)]]
                 result)]
    (into {} errors)))

