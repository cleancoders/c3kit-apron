### 2.1.0 (Feb 3, 2024)
 * c3kit.apron.schema overhaul - breaking changes
   * For 98% of the usage, there should be no visible changes, however there are some breaking changes.
   * Failed operation no longer return the unintelligible SchemaError. Instead, it returns a map where the problematic keys have FieldErrors as values.  This should be much more printable and readable.
   * Fixes bug where coerce, validate and conform returned different results
   * Error messages should be more detailed where possible

### July 14, 2023
 * c3kit.apron.app/start-env app must be the first parameter
