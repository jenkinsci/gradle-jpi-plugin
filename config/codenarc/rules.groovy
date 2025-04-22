// Custom CodeNarc ruleset for [Your Project]
// -----------------------------
// Licensed under the Apache License, Version 2.0

ruleset {
    description '''
    Enforces CodeNarc static analysis rules with project-specific customizations:
    - Core language rules
    - Conventions and style adjustments
    - Design and dry-run exceptions
    '''

    // ------------------------------------------------------------------------
    // Define exclusion groups for maintainability
    // ------------------------------------------------------------------------
    def conventionExcludes = [
        'FieldTypeRequired', 'MethodParameterTypeRequired', 'MethodReturnTypeRequired',
        'NoDef', 'VariableTypeRequired',
        'CompileStatic', 'ImplicitClosureParameter', 'ImplicitReturnStatement',
        'PublicMethodsBeforeNonPublicMethods', 'StaticFieldsBeforeInstanceFields',
        'StaticMethodsBeforeInstanceMethods'
    ]

    def designExcludes = [
        'Instanceof',  // sometimes needed for polymorphic checks
        'AbstractClassWithoutAbstractMethod'
    ]

    def dryExcludes = [
        'DuplicateListLiteral', 'DuplicateMapLiteral',
        'DuplicateNumberLiteral', 'DuplicateStringLiteral'
    ]

    def formattingExcludes = [
        'ClassJavadoc', 'ClassStartsWithBlankLine', 'ClassEndsWithBlankLine'
    ]

    def sizeExcludes = [
        'AbcMetric', 'CrapMetric', 'MethodSize'
    ]

    def unnecessaryExcludes = ['UnnecessaryGetter']

    // ------------------------------------------------------------------------
    // Include standard CodeNarc rulesets
    // ------------------------------------------------------------------------
    ['basic', 'braces', 'concurrency', 'exceptions', 'generic',
     'groovyism', 'imports', 'logging', 'security', 'serialization', 'unused']
    .each { rs -> ruleset("rulesets/${rs}.xml") }

    // ------------------------------------------------------------------------
    // Convention rules with project-specific exclusions
    // ------------------------------------------------------------------------
    ruleset('rulesets/convention.xml') {
        conventionExcludes.each { exclude it }
    }

    // ------------------------------------------------------------------------
    // Design rules
    // ------------------------------------------------------------------------
    ruleset('rulesets/design.xml') {
        designExcludes.each { exclude it }
    }

    // ------------------------------------------------------------------------
    // DRY rules
    // ------------------------------------------------------------------------
    ruleset('rulesets/dry.xml') {
        dryExcludes.each { exclude it }
    }

    // ------------------------------------------------------------------------
    // Formatting rules
    // ------------------------------------------------------------------------
    ruleset('rulesets/formatting.xml') {
        // enforce at least one space after map entry colon
        SpaceAroundMapEntryColon {
            characterAfterColonRegex  = /\s/
            characterBeforeColonRegex = /./
        }
        formattingExcludes.each { exclude it }
    }

    // ------------------------------------------------------------------------
    // Imports ordering: static imports after other imports
    // ------------------------------------------------------------------------
    ruleset('rulesets/imports.xml') {
        MisorderedStaticImports {
            comesBefore = false
        }
    }

    // ------------------------------------------------------------------------
    // Naming conventions
    // ------------------------------------------------------------------------
    ruleset('rulesets/naming.xml') {
        // Gradle build scripts may declare methods non-standard
        exclude 'ConfusingMethodName'
    }

    // ------------------------------------------------------------------------
    // Security
    // ------------------------------------------------------------------------
    ruleset('rulesets/security.xml') {
        // EJB spec not relevant to this project
        exclude 'JavaIoPackageAccess'
    }

    // ------------------------------------------------------------------------
    // Size (complexity) rules
    // ------------------------------------------------------------------------
    ruleset('rulesets/size.xml') {
        NestedBlockDepth {
            maxNestedBlockDepth = 6
        }
        sizeExcludes.each { exclude it }
    }

    // ------------------------------------------------------------------------
    // Unnecessary code rules
    // ------------------------------------------------------------------------
    ruleset('rulesets/unnecessary.xml') {
        unnecessaryExcludes.each { exclude it }
    }
}
