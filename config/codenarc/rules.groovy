ruleset {
    ruleset('rulesets/basic.xml')

    ruleset('rulesets/braces.xml')

    ruleset('rulesets/concurrency.xml')

    ruleset('rulesets/convention.xml') {
        // we don't care for now
        exclude 'FieldTypeRequired'
        // we don't care for now
        exclude 'MethodParameterTypeRequired\t'
        // we don't care for now
        exclude 'MethodReturnTypeRequired'
        // we don't care for now
        exclude 'NoDef'
        // we don't care for now
        exclude 'VariableTypeRequired'

        exclude 'CompileStatic'
        exclude 'ImplicitClosureParameter'
        exclude 'ImplicitReturnStatement'
        exclude 'PublicMethodsBeforeNonPublicMethods'
        exclude 'StaticFieldsBeforeInstanceFields'
        exclude 'StaticMethodsBeforeInstanceMethods'

    }

    ruleset('rulesets/design.xml') {
        // does not necessarily lead to better code
        exclude 'Instanceof'
        // needed for Worker Api
        exclude 'AbstractClassWithoutAbstractMethod'
    }

    ruleset('rulesets/dry.xml') {
        // does not necessarily lead to better code
        exclude 'DuplicateListLiteral'
        // does not necessarily lead to better code
        exclude 'DuplicateMapLiteral'
        // does not necessarily lead to better code
        exclude 'DuplicateNumberLiteral'
        // does not necessarily lead to better code
        exclude 'DuplicateStringLiteral'
    }

    // these rules cause compilation failure warnings
    // ruleset('rulesets/enhanced.xml')

    ruleset('rulesets/exceptions.xml')

    ruleset('rulesets/formatting.xml') {
        // enforce at least one space after map entry colon
        SpaceAroundMapEntryColon {
            characterAfterColonRegex = /\s/
            characterBeforeColonRegex = /./
        }

        // we don't care for now
        exclude 'ClassJavadoc'
        exclude 'ClassStartsWithBlankLine'
        exclude 'ClassEndsWithBlankLine'
    }

    ruleset('rulesets/generic.xml')

    ruleset('rulesets/groovyism.xml')

    ruleset('rulesets/imports.xml') {
        // we order static imports after other imports because that's the default style in IDEA
        MisorderedStaticImports {
            comesBefore = false
        }
    }

    ruleset('rulesets/logging.xml')

    ruleset('rulesets/naming.xml') {
        // Gradle encourages violations of this rule
        exclude 'ConfusingMethodName'
    }

    ruleset('rulesets/security.xml') {
        // we don't care for the Enterprise Java Bean specification here
        exclude 'JavaIoPackageAccess'
    }

    ruleset('rulesets/serialization.xml')

    ruleset('rulesets/size.xml') {
        NestedBlockDepth {
            maxNestedBlockDepth = 6
        }

        // we don't care for now
        exclude 'AbcMetric'
        // we have no Cobertura coverage file yet
        exclude 'CrapMetric'
        // we don't care for now
        exclude 'MethodSize'
    }

    ruleset('rulesets/unnecessary.xml') {
        exclude 'UnnecessaryGetter'
    }

    ruleset('rulesets/unused.xml')
}
