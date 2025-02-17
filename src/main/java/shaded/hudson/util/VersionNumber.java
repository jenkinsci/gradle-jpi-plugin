package shaded.hudson.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Properties;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable representation of a version number based on the Mercury version numbering scheme.
 *
 * {@link VersionNumber}s are {@link Comparable}.
 *
 * <h2>Special tokens</h2>
 * <p>
 * We allow a component to be not just a number, but also "ea", "ea1", "ea2".
 * "ea" is treated as "ea0", and eaN &lt; M for any M &lt; 0.
 *
 * <p>
 * '*' is also allowed as a component, and '*' &lt; M for any M &lt; 0.
 *
 * <p>
 * 'SNAPSHOT' is also allowed as a component, and "N.SNAPSHOT" is interpreted as "N-1.*"
 *
 * This class is re-implemented in 1.415. The class was originally introduced in 1.139
 *
 * @since 1.139
 * @author Stephen Connolly (stephenc@apache.org)
 * @author Kenney Westerhof (kenney@apache.org)
 * @author Hervé Boutemy (hboutemy@apache.org)
 */
public class VersionNumber implements Comparable<VersionNumber> {

    private static final Pattern SNAPSHOT = Pattern.compile("^.*((?:-\\d{8}\\.\\d{6}-\\d+)|-SNAPSHOT)( \\(.*\\))?$");

    private String value;

    private String snapshot;

    private String canonical;

    private ListItem items;

    private interface Item {
        int INTEGER_ITEM = 0;

        int STRING_ITEM = 1;

        int LIST_ITEM = 2;

        int WILDCARD_ITEM = 3;

        int compareTo(Item item);

        int getType();

        boolean isNull();
    }

    /**
     * Represents a wild-card item in the version item list.
     */
    private static class WildCardItem implements Item {

        @Override
        public int compareTo(Item item) {
            if (item==null) // 1.* ( > 1.99) > 1
                return 1;
            switch (item.getType()) {
                case INTEGER_ITEM:
                case LIST_ITEM:
                case STRING_ITEM:
                    return 1;
                case WILDCARD_ITEM:
                    return 0;
                default:
                    return 1;
            }
        }

        @Override
        public int getType() {
            return WILDCARD_ITEM;
        }

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public String toString() {
            return "*";
        }
    }

    /**
     * Represents a numeric item in the version item list.
     */
    private static class IntegerItem
            implements Item {
        private static final BigInteger BigInteger_ZERO = new BigInteger("0");

        private final BigInteger value;

        public static final IntegerItem ZERO = new IntegerItem();

        private IntegerItem() {
            this.value = BigInteger_ZERO;
        }

        public IntegerItem(String str) {
            this.value = new BigInteger(str);
        }

        @Override
        public int getType() {
            return INTEGER_ITEM;
        }

        @Override
        public boolean isNull() {
            return BigInteger_ZERO.equals(value);
        }

        @Override
        public int compareTo(Item item) {
            if (item == null) {
                return BigInteger_ZERO.equals(value) ? 0 : 1; // 1.0 == 1, 1.1 > 1
            }

            switch (item.getType()) {
                case INTEGER_ITEM:
                    return value.compareTo(((IntegerItem) item).value);

                case STRING_ITEM:
                    return 1; // 1.1 > 1-sp

                case LIST_ITEM:
                    return 1; // 1.1 > 1-1

                case WILDCARD_ITEM:
                    return 0;

                default:
                    throw new IllegalArgumentException("invalid item: " + item.getClass());
            }
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    /**
     * Represents a string in the version item list, usually a qualifier.
     */
    private static class StringItem implements Item {
        private final static String[] QUALIFIERS = {"snapshot", "alpha", "beta", "milestone", "rc", "", "sp"};

        private final static List<String> _QUALIFIERS = Arrays.asList(QUALIFIERS);

        private final static Properties ALIASES = new Properties();

        static {
            ALIASES.put("ga", "");
            ALIASES.put("final", "");
            ALIASES.put("cr", "rc");
            ALIASES.put("ea", "rc");
        }

        /**
         * A comparable for the empty-string qualifier. This one is used to determine if a given qualifier makes the
         * version older than one without a qualifier, or more recent.
         */
        private static String RELEASE_VERSION_INDEX = String.valueOf(_QUALIFIERS.indexOf(""));

        private String value;

        public StringItem(String value, boolean followedByDigit) {
            if (followedByDigit && value.length() == 1) {
                // a1 = alpha-1, b1 = beta-1, m1 = milestone-1
                switch (value.charAt(0)) {
                    case 'a':
                        value = "alpha";
                        break;
                    case 'b':
                        value = "beta";
                        break;
                    case 'm':
                        value = "milestone";
                        break;
                    default:
                        /* fall through? */
                }
            }
            this.value = ALIASES.getProperty(value, value);
        }

        @Override
        public int getType() {
            return STRING_ITEM;
        }

        @Override
        public boolean isNull() {
            return (comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX) == 0);
        }

        /**
         * Returns a comparable for a qualifier.
         * <p/>
         * This method both takes into account the ordering of known qualifiers as well as lexical ordering for unknown
         * qualifiers.
         * <p/>
         * just returning an Integer with the index here is faster, but requires a lot of if/then/else to check for -1
         * or QUALIFIERS.size and then resort to lexical ordering. Most comparisons are decided by the first character,
         * so this is still fast. If more characters are needed then it requires a lexical sort anyway.
         */
        public static String comparableQualifier(String qualifier) {
            int i = _QUALIFIERS.indexOf(qualifier);

            return i == -1 ? _QUALIFIERS.size() + "-" + qualifier : String.valueOf(i);
        }

        @Override
        public int compareTo(Item item) {
            if (item == null) {
                // 1-rc < 1, 1-ga > 1
                return comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX);
            }
            switch (item.getType()) {
                case INTEGER_ITEM:
                    return -1; // 1.any < 1.1 ?

                case STRING_ITEM:
                    return comparableQualifier(value).compareTo(comparableQualifier(((StringItem) item).value));

                case LIST_ITEM:
                    return -1; // 1.any < 1-1

                case WILDCARD_ITEM:
                    return -1;

                default:
                    throw new IllegalArgumentException("invalid item: " + item.getClass());
            }
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Represents a version list item. This class is used both for the global item list and for sub-lists (which start
     * with '-(number)' in the version specification).
     */
    private static class ListItem extends ArrayList<Item> implements Item {
        @Override
        public int getType() {
            return LIST_ITEM;
        }

        @Override
        public boolean isNull() {
            return (size() == 0);
        }

        void normalize() {
            for (ListIterator<Item> iterator = listIterator(size()); iterator.hasPrevious(); ) {
                Item item = iterator.previous();
                if (item.isNull()) {
                    iterator.remove(); // remove null trailing items: 0, "", empty list
                } else {
                    break;
                }
            }
        }

        @Override
        public int compareTo(Item item) {
            if (item == null) {
                if (size() == 0) {
                    return 0; // 1-0 = 1- (normalize) = 1
                }
                Item first = get(0);
                return first.compareTo(null);
            }

            switch (item.getType()) {
                case INTEGER_ITEM:
                    return -1; // 1-1 < 1.0.x

                case STRING_ITEM:
                    return 1; // 1-1 > 1-sp

                case LIST_ITEM:
                    Iterator<Item> left = iterator();
                    Iterator<Item> right = ((ListItem) item).iterator();

                    while (left.hasNext() || right.hasNext()) {
                        Item l = left.hasNext() ? left.next() : null;
                        Item r = right.hasNext() ? right.next() : null;

                        // if this is shorter, then invert the compare and mul with -1
                        int result;
                        if (l == null) {
                            if (r == null) {
                                result = 0;
                            } else {
                                result = -1 * r.compareTo(null);
                            }
                        } else {
                            result = l.compareTo(r);
                        }

                        if (result != 0) {
                            return result;
                        }
                    }

                    return 0;

                case WILDCARD_ITEM:
                    return -1;

                default:
                    throw new IllegalArgumentException("invalid item: " + item.getClass());
            }
        }

        @Override
        public String toString() {
            StringBuilder buffer = new StringBuilder("(");
            for (Iterator<Item> iter = iterator(); iter.hasNext(); ) {
                buffer.append(iter.next());
                if (iter.hasNext()) {
                    buffer.append(',');
                }
            }
            buffer.append(')');
            return buffer.toString();
        }
    }

    public VersionNumber(String version) {
        parseVersion(version);
    }

    private void parseVersion(String version) {
        this.value = version;


        items = new ListItem();

        Matcher matcher = SNAPSHOT.matcher(version);
        if (matcher.matches()) {
            snapshot = matcher.group(1);
            version = version.substring(0, matcher.start(1)) + "-SNAPSHOT";
        }
        version = version.toLowerCase(Locale.ENGLISH);

        ListItem list = items;

        Stack<Item> stack = new Stack<>();
        stack.push(list);

        boolean isDigit = false;

        int startIndex = 0;

        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);

            if (c == '.') {
                if (i == startIndex) {
                    list.add(IntegerItem.ZERO);
                } else {
                    list.add(parseItem(isDigit, version.substring(startIndex, i)));
                }
                startIndex = i + 1;
            } else if (c == '-') {
                if (i == startIndex) {
                    list.add(IntegerItem.ZERO);
                } else {
                    list.add(parseItem(isDigit, version.substring(startIndex, i)));
                }
                startIndex = i + 1;

                if (isDigit) {
                    list.normalize(); // 1.0-* = 1-*

                    if ((i + 1 < version.length()) && Character.isDigit(version.charAt(i + 1))) {
                        // new ListItem only if previous were digits and new char is a digit,
                        // ie need to differentiate only 1.1 from 1-1
                        list.add(list = new ListItem());

                        stack.push(list);
                    }
                }
            } else if (c == '*') {
                list.add(new WildCardItem());
                startIndex = i + 1;
            } else if (Character.isDigit(c)) {
                if (!isDigit && i > startIndex) {
                    list.add(new StringItem(version.substring(startIndex, i), true));
                    startIndex = i;
                }

                isDigit = true;
            } else if (Character.isWhitespace(c)) {
                if (i > startIndex) {
                    if (isDigit) {
                        list.add(parseItem(true, version.substring(startIndex, i)));
                    } else {
                        list.add(new StringItem(version.substring(startIndex, i), true));
                    }
                    startIndex = i;
                }

                isDigit = false;
            } else {
                if (isDigit && i > startIndex) {
                    list.add(parseItem(true, version.substring(startIndex, i)));
                    startIndex = i;
                }

                isDigit = false;
            }
        }

        if (version.length() > startIndex) {
            list.add(parseItem(isDigit, version.substring(startIndex)));
        }

        while (!stack.isEmpty()) {
            list = (ListItem) stack.pop();
            list.normalize();
        }

        canonical = items.toString();
    }

    private static Item parseItem(boolean isDigit, String buf) {
        return isDigit ? new IntegerItem(buf) : new StringItem(buf, false);
    }

    @Override
    public int compareTo(VersionNumber o) {
        int result = items.compareTo(o.items);
        if (result != 0) {
            return result;
        }
        if (snapshot == null) {
            return o.snapshot == null ? 0 : -1;
        }
        if (o.snapshot == null) {
            return 1;
        }
        if ("-SNAPSHOT".equals(snapshot) || "-SNAPSHOT".equals(o.snapshot)) {
            // cannot compare literal with timestamped.
            return 0;
        }
        result = snapshot.substring(1, 16).compareTo(o.snapshot.substring(1, 16));
        if (result != 0) {
            return result;
        }
        int i1 = Integer.parseInt(snapshot.substring(17));
        int i2 = Integer.parseInt(o.snapshot.substring(17));
        return Integer.compare(i1, i2);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VersionNumber)) {
            return false;
        }
        VersionNumber that = (VersionNumber) o;
        if (!canonical.equals(that.canonical)) {
            return false;
        }
        if (snapshot == null) {
            return that.snapshot == null;
        }
        if ("-SNAPSHOT".equals(snapshot) || "-SNAPSHOT".equals(that.snapshot)) {
            // text snapshots always match text or timestamped
            return true;
        }
        return snapshot.equals(that.snapshot);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }

    public boolean isOlderThan(VersionNumber rhs) {
        return compareTo(rhs) < 0;
    }

    public boolean isNewerThan(VersionNumber rhs) {
        return compareTo(rhs) > 0;
    }

    public boolean isOlderThanOrEqualTo(VersionNumber rhs) {
        return compareTo(rhs) <= 0;
    }

    public boolean isNewerThanOrEqualTo(VersionNumber rhs) {
        return compareTo(rhs) >= 0;
    }

    /**
     * Returns a digit (numeric component) by its position. Once a non-numeric component is found all remaining components
     * are also considered non-numeric by this method.
     *
     * @param idx Digit position we want to retrieve starting by 0.
     * @return The digit or -1 in case the position does not correspond with a digit.
     * @deprecated see {@link #getDigitAt(int)}
     */
    @Deprecated
    public int digit(int idx) {
        Iterator<Item> i = items.iterator();
        Item item = i.next();
        while (idx > 0 && i.hasNext()) {
            if (item instanceof IntegerItem) {
                idx--;
            }
            i.next();
        }
        return ((IntegerItem) item).value.intValue();
    }

    /**
     * Returns a digit (numeric component) by its position. Once a non-numeric component is found all remaining components
     * are also considered non-numeric by this method.
     *
     * @param idx Digit position we want to retrieve starting by 0.
     * @return The digit or -1 in case the position does not correspond with a digit.
     */
    public int getDigitAt(int idx) {
        if (idx < 0) {
            return -1;
        }

        Iterator<Item> it = items.iterator();
        int i = 0;
        Item item = null;
        while (i <= idx && it.hasNext()) {
            item  = it.next();
            if (item instanceof IntegerItem) {
                i++;
            } else {
                return -1;
            }
        }
        return (idx - i >= 0) ? -1 : ((IntegerItem) item).value.intValue();
    }

    public static final Comparator<VersionNumber> DESCENDING = Comparator.reverseOrder();
}
