ruleset {
    ruleset('rulesets/junit.xml') {
        // Spock ...
        exclude 'JUnitPublicNonTestMethod'
        exclude 'JUnitPublicProperty'
    }

    ruleset('file:config/codenarc/rules.groovy') {
        // Spock encourages to violate this rule
        exclude 'MethodName'
        // OK for tests
        exclude 'Instanceof'
        // No line length limit for tests
        exclude 'LineLength'
        // Spock ...
        exclude 'UnnecessaryBooleanExpression'
    }
}
