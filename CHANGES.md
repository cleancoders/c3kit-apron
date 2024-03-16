### x.x.x (Feb 7, 2024)
 * MAJOR BREAKING CHANGES - c3kit.apron.schema
   * Fields with seq types are now treated as first class.
     * `{:names {:type [:string]}}` is no longer allowed.  
     * Seq fields must have specs: `{:names {:type [{:type :int}]}}`
     * process fns defined on the seq field are now applied to the seq field instead of the values in the seq.
     * process fns defined on the seq field spec (first and only value of the seq field type) will be applied to each value in the seq
   * rename `<process>-errors` to `<process>-message-map`

### 2.1.0 (Feb 3, 2024)
 * c3kit.apron.schema overhaul - minor breaking changes
   * For 98% of the usage, there should be no visible changes, however there are some breaking changes.
   * Failed operation no longer return the unintelligible SchemaError. Instead, it returns a map where the problematic keys have FieldErrors as values.  This should be much more printable and readable.
   * Fixes bug where coerce, validate and conform returned different results
   * Error messages should be more detailed where possible

### July 14, 2023
 * c3kit.apron.app/start-env app must be the first parameter
