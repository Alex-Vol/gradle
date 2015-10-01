/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CustomBinaryInternalViewsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            apply plugin: "jvm-component"

            interface SampleBinarySpec extends BinarySpec {
                String getPublicData()
                void setPublicData(String publicData)
            }

            interface SampleBinarySpecInternal extends BinarySpec {
                String getInternalData()
                void setInternalData(String internalData)
            }

            class DefaultSampleBinarySpec extends BaseBinarySpec implements SampleBinarySpec, SampleBinarySpecInternal {
                String internalData
                String publicData
            }
        """
    }

    def setupRegistration() {
        buildFile << """
            class RegisterBinaryRules extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinarySpec> builder) {
                    builder.defaultImplementation(DefaultSampleBinarySpec)
                    builder.internalView(SampleBinarySpecInternal)
                }
            }
            apply plugin: RegisterBinaryRules
        """
    }

    def setupModel() {
        buildFile << """
            model {
                components {
                    sampleLib(JvmLibrarySpec) {
                        binaries {
                            sampleBin(SampleBinarySpec)
                        }
                    }
                }
            }
        """
    }

    def setupValidateTask() {
        buildFile << """
        class ValidateTaskRules extends RuleSource {
            @Mutate
            void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                tasks.create("validate") {
                    def binaries = components.sampleLib.binaries
                    assert binaries*.name == ["sampleBin", "sampleLibJar"]
                    assert binaries.withType(BinarySpec)*.name == ["sampleBin", "sampleLibJar"]
                    assert binaries.withType(JvmBinarySpec)*.name == ["sampleLibJar"]
                    assert binaries.withType(SampleBinarySpec)*.name == ["sampleBin"]
                    assert binaries.withType(SampleBinarySpecInternal)*.name == ["sampleBin"]
                }
            }
        }
        apply plugin: ValidateTaskRules
        """
    }

    def "can target top-level internal view with rules"() {
        setupRegistration()
        setupModel()

        buildFile << """

        class Rules extends RuleSource {
            @Finalize
            void mutateInternal(ModelMap<SampleBinarySpecInternal> sampleBins) {
                sampleBins.each { sampleBin ->
                    sampleBin.internalData = "internal"
                }
            }

            @Finalize
            void mutatePublic(ModelMap<SampleBinarySpec> sampleBins) {
                sampleBins.each { sampleBin ->
                    sampleBin.publicData = "public"
                }
            }

            @Mutate
            void createValidateTask(ModelMap<Task> tasks, ModelMap<SampleBinarySpecInternal> sampleLibs) {
                tasks.create("validate") {
                    sampleLibs.each { sampleLib ->
                        assert sampleLib.internalData == "internal"
                        assert sampleLib.publicData == "public"
                    }
                }
            }
        }
        apply plugin: Rules
        """
        expect:
        succeeds "validate"
    }

    def "can target component's binaries via withType()"() {
        setupRegistration()
        setupModel()

        buildFile << """

        class Rules extends RuleSource {
            @Mutate
            void mutateInternal(ModelMap<ComponentSpec> libs) {
                libs.all { lib ->
                    lib.binaries.withType(SampleBinarySpecInternal) { sampleBin ->
                        sampleBin.internalData = "internal"
                    }
                }
            }

            @Mutate
            void mutatePublic(ModelMap<ComponentSpec> libs) {
                libs.all { lib ->
                    lib.binaries.withType(SampleBinarySpec) { sampleBin ->
                        sampleBin.publicData = "public"
                    }
                }
            }

            @Mutate
            void createValidateTask(ModelMap<Task> tasks, ModelMap<SampleBinarySpecInternal> sampleLibs) {
                tasks.create("validate") {
                    sampleLibs.each { sampleLib ->
                        assert sampleLib.internalData == "internal"
                        assert sampleLib.publicData == "public"
                    }
                }
            }
        }
        apply plugin: Rules
        """
        expect:
        succeeds "validate"
    }

    def "can filter for custom internal view with BinarySpecContainer.withType()"() {
        setupRegistration()
        setupModel()
        setupValidateTask()

        expect:
        succeeds "validate"
    }

    def "can filter for custom internal view with BinariesContainer.withType()"() {
        setupRegistration()
        setupModel()
        buildFile << """
        class ValidateTaskRules extends RuleSource {
            @Mutate
            void createValidateTask(ModelMap<Task> tasks, BinaryContainer binaries) {
                tasks.create("validate") {
                    assert binaries*.name == ["sampleBin", "sampleLibJar"]
                    assert binaries.withType(BinarySpec)*.name == ["sampleBin", "sampleLibJar"]
                    assert binaries.withType(JvmBinarySpec)*.name == ["sampleLibJar"]
                    assert binaries.withType(SampleBinarySpec)*.name == ["sampleBin"]
                    assert binaries.withType(SampleBinarySpecInternal)*.name == ["sampleBin"]
                }
            }
        }
        apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"
    }

    def "can register internal view and default implementation separately"() {
        buildFile << """
            class RegisterBinaryRules extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinarySpec> builder) {
                    builder.defaultImplementation(DefaultSampleBinarySpec)
                }

                @BinaryType
                void registerInternalView(BinaryTypeBuilder<SampleBinarySpec> builder) {
                    builder.internalView(SampleBinarySpecInternal)
                }
            }
            apply plugin: RegisterBinaryRules
        """

        setupModel()
        setupValidateTask()

        expect:
        succeeds "validate"
    }

    def "fails when wrong internal view is registered separately"() {
        buildFile << """
            interface NotImplementedInternalView extends BinarySpec {}

            class RegisterBinaryRules extends RuleSource {
                @BinaryType
                void registerBinary(BinaryTypeBuilder<SampleBinarySpec> builder) {
                    builder.defaultImplementation(DefaultSampleBinarySpec)
                }

                @BinaryType
                void registerInternalView(BinaryTypeBuilder<SampleBinarySpec> builder) {
                    builder.internalView(NotImplementedInternalView)
                }
            }
            apply plugin: RegisterBinaryRules
        """

        setupModel()

        expect:
        def failure = fails("components")
        failure.assertHasCause "Factory registration for 'SampleBinarySpec' is invalid because the implementation type 'DefaultSampleBinarySpec' does not extend internal view 'NotImplementedInternalView'."
    }
}
