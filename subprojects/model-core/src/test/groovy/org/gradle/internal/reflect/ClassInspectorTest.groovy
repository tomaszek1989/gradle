/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.reflect

import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import spock.lang.Issue
import spock.lang.Specification

class ClassInspectorTest extends Specification {
    def inspector = new ClassInspector(new TestCrossBuildInMemoryCacheFactory())

    def "extracts properties of a Java class"() {
        expect:
        def details = inspect(JavaTestSubject)

        details.propertyNames == ['class', 'myProperty', 'myBooleanProperty', 'myOtherBooleanProperty', 'writeOnly', 'protectedProperty', 'multiValue', 'myProperty2', 'myProperty3'] as Set
    }

    def "extracts properties of a Groovy class"() {
        expect:
        def details = inspect(SomeClass)

        details.propertyNames == ['class', 'metaClass', 'prop', 'readOnly', 'writeOnly'] as Set

        def prop = details.getProperty('prop')
        prop.getters.size() == 1
        prop.setters.size() == 1

        def readOnly = details.getProperty('readOnly')
        readOnly.getters.size() == 1
        readOnly.setters.size() == 0

        def writeOnly = details.getProperty('writeOnly')
        writeOnly.getters.size() == 0
        writeOnly.setters.size() == 1
    }

    def "extracts property names"() {
        expect:
        def details = inspect(PropNames)

        details.propertyNames == ['class', 'metaClass', 'a', 'b', 'URL', 'url', '_A'] as Set
    }

    def "extracts properties of a Groovy interface"() {
        expect:
        def details = inspect(SomeInterface)

        details.propertyNames == ['prop', 'readOnly', 'writeOnly'] as Set

        def prop = details.getProperty('prop')
        prop.getters.size() == 1
        prop.setters.size() == 1

        def readOnly = details.getProperty('readOnly')
        readOnly.getters.size() == 1
        readOnly.setters.size() == 0

        def writeOnly = details.getProperty('writeOnly')
        writeOnly.getters.size() == 0
        writeOnly.setters.size() == 1
    }

    def "extracts boolean properties"() {
        expect:
        def details = inspect(BooleanProps)

        details.propertyNames == ['class', 'metaClass', 'prop', 'someProp', 'readOnly'] as Set

        def prop = details.getProperty('prop')
        prop.getters.size() == 2
        prop.setters.size() == 1

        def readOnly = details.getProperty('readOnly')
        readOnly.getters.size() == 1
        readOnly.setters.size() == 0

        def someProp = details.getProperty('someProp')
        someProp.getters.size() == 1
        someProp.setters.size() == 1
    }

    def "extracts properties with overloaded getters and setters"() {
        expect:
        def details = inspect(Overloads)

        def prop = details.getProperty('prop')
        prop.getters.size() == 2
        prop.setters.size() == 3
    }

    def "extracts properties from super class"() {
        expect:
        def details = inspect(SubClass)

        details.propertyNames == ['class', 'metaClass', 'prop', 'readOnly', 'writeOnly', 'other'] as Set

        def prop = details.getProperty('prop')
        prop.getters.size() == 1
        prop.setters.size() == 1

        def other = details.getProperty('other')
        other.getters.size() == 1
        other.setters.size() == 1

        def readOnly = details.getProperty('readOnly')
        readOnly.getters.size() == 1
        readOnly.setters.size() == 0

        def writeOnly = details.getProperty('writeOnly')
        writeOnly.getters.size() == 0
        writeOnly.setters.size() == 1
    }

    def "extracts properties from super interface"() {
        expect:
        def details = inspect(SubInterface)

        details.propertyNames == ['prop', 'readOnly', 'writeOnly', 'other'] as Set

        def prop = details.getProperty('prop')
        prop.getters.size() == 1
        prop.setters.size() == 1

        def other = details.getProperty('other')
        other.getters.size() == 1
        other.setters.size() == 1

        def readOnly = details.getProperty('readOnly')
        readOnly.getters.size() == 1
        readOnly.setters.size() == 0

        def writeOnly = details.getProperty('writeOnly')
        writeOnly.getters.size() == 0
        writeOnly.setters.size() == 1
    }

    def "extracts properties from super type graph"() {
        expect:
        def details = inspect(AbstractSubClass)

        details.propertyNames == ['class', 'metaClass', 'prop', 'readOnly', 'writeOnly', 'other'] as Set

        def prop = details.getProperty('prop')
        prop.getters.size() == 1
        prop.setters.size() == 1

        def other = details.getProperty('other')
        other.getters.size() == 1
        other.setters.size() == 1

        def readOnly = details.getProperty('readOnly')
        readOnly.getters.size() == 1
        readOnly.setters.size() == 0

        def writeOnly = details.getProperty('writeOnly')
        writeOnly.getters.size() == 0
        writeOnly.setters.size() == 1
    }

    def "subtype can specialize a property type"() {
        expect:
        def details = inspect(SpecializingType)

        details.propertyNames == ['class', 'metaClass', 'prop', 'readOnly', 'writeOnly'] as Set

        def prop = details.getProperty('prop')
        prop.getters.size() == 2
        prop.setters.size() == 2

        def readOnly = details.getProperty('readOnly')
        readOnly.getters.size() == 1
        readOnly.setters.size() == 1

        def writeOnly = details.getProperty('writeOnly')
        writeOnly.getters.size() == 1
        writeOnly.setters.size() == 1
    }

    def "collects instance methods that are not getters or setters"() {
        expect:
        def details = inspect(SomeClass)

        details.instanceMethods.find { it.name == 'prop' }
        !details.instanceMethods.contains(details.getProperty('prop').getters[0])
    }

    def "ignores overridden property getters and setters"() {
        expect:
        def details = inspect(Overrides)

        def prop = details.getProperty('prop')
        prop.getters.size() == 1
        prop.getters[0].declaringClass == Overrides
        prop.setters.size() == 1
        prop.setters[0].declaringClass == Overrides
    }

    def "ignores overridden instance methods"() {
        expect:
        def details = inspect(Overrides)

        def methods = details.instanceMethods.findAll { it.name == 'prop' }
        methods.size() == 1
        methods[0].declaringClass == Overrides
    }

    def inspect(Class<?> type) {
        return inspector.inspect(type)
    }

    class SomeClass {
        Number prop

        String getReadOnly() {
            return prop
        }

        void setWriteOnly(String value) {
        }

        void get() {
        }

        void set(String value) {
        }

        void getProp(String value) {
        }

        void setProp() {
        }

        protected String prop() {
            return prop
        }

        static String ignoredStatic
        private String ignoredPrivate

        private Number getPrivate() {
            return 12
        }

        static Long getLong() {
            return 12L
        }
    }

    interface SomeInterface {
        Number getProp()

        void setProp(Number value)

        String getReadOnly()

        void setWriteOnly(String value)

        void get()

        void set(String value)

        void getProp(String value)

        void setProp()
    }

    class PropNames {
        String getA() { null }

        String getB() { null }

        String getURL() { null }

        String getUrl() { null }

        String get_A() { null }
    }

    class BooleanProps {
        boolean prop

        boolean isSomeProp() {
            return prop
        }

        void setSomeProp(boolean value) {
        }

        Boolean isReadOnly() {
            return prop
        }
    }

    class Overloads {
        String getProp() {
            return null
        }

        boolean isProp() {
            return false
        }

        void setProp(String value) {
        }

        void setProp(int value) {
        }

        void setProp(Object value) {
        }
    }

    class SubClass extends SomeClass {
        Long other
    }

    interface SubInterface extends SomeInterface {
        Long getOther()

        void setOther(Long l)
    }

    abstract class AbstractSuperClass implements SomeInterface {
        Number prop
    }

    abstract class AbstractSubClass extends AbstractSuperClass implements SubInterface {
        Long other
    }

    class Overrides extends SomeClass {
        @Override
        Number getProp() {
            return super.getProp()
        }

        @Override
        void setProp(Number prop) {
            super.setProp(prop)
        }

        @Override
        protected String prop() {
            return "123"
        }
    }

    class SpecializingType extends SomeClass {
        Long getProp() {
            return 12L
        }

        void setProp(Long l) {
        }

        void setReadOnly(String s) {
        }

        String getWriteOnly() {
            return ""
        }
    }

    def "can extract super types"() {
        expect:
        inspect(Object).superTypes.empty
        inspect(Serializable).superTypes.empty
        inspect(List).superTypes.toList() == [Collection, Iterable]
    }

    def "super types ordered by their distance"() {
        expect:
        inspect(ArrayList).superTypes.toList() == [AbstractList, AbstractCollection, Object, List, RandomAccess, Cloneable, Serializable, Collection, Iterable]
    }

    @Issue("GRADLE-3317")
    def "method for property getter is for nearest declaration"() {
        expect:
        def details = inspect(clazz)
        def getters = details.getProperty('inputs').getGetters()
        getters[0].getDeclaringClass() == declaringClass

        where:
        clazz                                                        | declaringClass
        ImplementingRootClassSubSubImplementsRootInterfaceSub        | ImplementingRootClassSub
        ExtendsImplementingRootClassSubSubImplementsRootInterfaceSub | ExtendsImplementingRootClassSubSubImplementsRootInterfaceSub
        ExtendsImplementingRootClass                                 | ImplementingRootClass
    }

    public interface RootInterface {
        public String getInputs()
    }

    public interface RootInterfaceSub extends RootInterface {}

    public class ImplementingRootClass implements RootInterface {
        public String getInputs() { return "super" }
    }

    public class ImplementingRootClassSub extends ImplementingRootClass {
        public String getInputs() { return "concrete" }
    }

    public class ImplementingRootClassSubSubImplementsRootInterfaceSub extends ImplementingRootClassSub implements RootInterfaceSub {}

    public class ExtendsImplementingRootClassSubSubImplementsRootInterfaceSub extends ImplementingRootClassSubSubImplementsRootInterfaceSub {
        public String getInputs() { return "override" }
    }

    public class ExtendsImplementingRootClass extends ImplementingRootClass {}
}
