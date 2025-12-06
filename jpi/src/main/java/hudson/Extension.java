/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson;

import jenkins.YesNoMaybe;

import static jenkins.YesNoMaybe.MAYBE;

/**
 * Minimal clone for auto-detection.
 *
 * @author Kohsuke Kawaguchi
 */
public @interface Extension {
    /**
     * Specifies the ordinal of this extension, which determines its sort order
     * relative to other extensions of the same type.
     * <p>
     * Higher ordinal values have higher priority.
     *
     * @return the ordinal value (default: 0)
     */
    double ordinal() default 0;

    /**
     * Specifies whether this extension is optional.
     * <p>
     * Optional extensions can be disabled by the user.
     *
     * @return true if this extension is optional, false otherwise
     */
    boolean optional() default false;

    /**
     * Specifies whether this extension can be loaded dynamically.
     * <p>
     * This affects how the extension is loaded in Jenkins.
     *
     * @return the dynamic loading preference (default: MAYBE)
     */
    YesNoMaybe dynamicLoadable() default MAYBE;
}
