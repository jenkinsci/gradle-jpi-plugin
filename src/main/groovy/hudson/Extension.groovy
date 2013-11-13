package hudson

import jenkins.YesNoMaybe

/**
 * @author Kiyotaka Oku
 */

public @interface Extension {
    YesNoMaybe dynamicLoadable() default YesNoMaybe.MAYBE;
}