(use 'clj-unit.core)
(require-and-run-tests
  'fleetdb.core-test
  'fleetdb.planner-test
  'fleetdb.embedded-test
  'fleetdb.client-test
)