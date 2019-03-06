/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class IncrementalTasksIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        setupTaskSources()
        buildFile << buildFileBase
        buildFile << """
    task incremental(type: IncrementalTask) {
        inputDir = project.mkdir('inputs')
        outputDir = project.mkdir('outputs')
        prop = 'foo'
    }
"""
        file('inputs/file0.txt') << "inputFile0"
        file('inputs/file1.txt') << "inputFile1"
        file('inputs/file2.txt') << "inputFile2"

        file('outputs/file1.txt') << "outputFile1"
        file('outputs/file2.txt') << "outputFile2"
    }

    private void setupTaskSources() {
        file("buildSrc/src/main/groovy/BaseIncrementalTask.groovy") << """
    import org.gradle.api.*
    import org.gradle.api.plugins.*
    import org.gradle.api.tasks.*
    import org.gradle.api.tasks.incremental.*
    import org.gradle.api.execution.incremental.*

    class BaseIncrementalTask extends DefaultTask {
        @InputDirectory
        def File inputDir

        @TaskAction
        void execute(IncrementalInputs inputs) {
            assert !(inputs instanceof ExtensionAware)

            if (project.hasProperty('forceFail')) {
                throw new RuntimeException('failed')
            }

            incrementalExecution = inputs.incremental

            inputs.getChanges(inputDir).each { change ->
                if (change.added) {
                    addedFiles << change.file
                } else if (change.modified) {
                    changedFiles << change.file
                } else {
                    removedFiles << change.file
                }
            }

            if (!inputs.incremental) {
                createOutputsNonIncremental()
            }
            
            touchOutputs()
        }

        def touchOutputs() {
        }

        def createOutputsNonIncremental() {
        }

        def addedFiles = []
        def changedFiles = []
        def removedFiles = []
        def incrementalExecution
    }
        """
        file("buildSrc/src/main/groovy/IncrementalTask.groovy") << """
    import org.gradle.api.*
    import org.gradle.api.plugins.*
    import org.gradle.api.tasks.*
    import org.gradle.api.tasks.incremental.*

    class IncrementalTask extends BaseIncrementalTask {
        @Input
        def String prop

        @OutputDirectory
        def File outputDir

        @Override
        def createOutputsNonIncremental() {
            new File(outputDir, 'file1.txt').text = 'outputFile1'
            new File(outputDir, 'file2.txt').text = 'outputFile2'
        }

        @Override
        def touchOutputs() {
            outputDir.eachFile {
                it << "more content"
            }
        }
    }
"""
    }

    private static String getBuildFileBase() {
        """
    ext {
        incrementalExecution = true
        added = []
        changed = []
        removed = []
    }

    task incrementalCheck(dependsOn: "incremental") {
        doLast {
            assert incremental.incrementalExecution == project.ext.incrementalExecution
            assert incremental.addedFiles.collect({ it.name }).sort() == project.ext.added
            assert incremental.changedFiles.collect({ it.name }).sort() == project.ext.changed
            assert incremental.removedFiles.collect({ it.name }).sort() == project.ext.removed
        }
    }
"""
    }

    def "incremental task is informed that all input files are 'out-of-date' when run for the first time"() {
        expect:
        executesWithRebuildContext()
    }

    def "incremental task is skipped when run with no changes since last execution"() {
        given:
        previousExecution()

        when:
        run "incremental"

        then:
        ":incremental" in skippedTasks
    }

    def "incremental task is informed of 'out-of-date' files when input file modified"() {
        given:
        previousExecution()

        when:
        file('inputs/file1.txt') << "changed content"

        then:
        executesWithIncrementalContext("ext.changed = ['file1.txt']")
    }

    def "incremental task is informed of 'out-of-date' files when input file added"() {
        given:
        previousExecution()

        when:
        file('inputs/file3.txt') << "file3 content"

        then:
        executesWithIncrementalContext("ext.added = ['file3.txt']")
    }

    def "incremental task is informed of 'out-of-date' files when input file removed"() {
        given:
        previousExecution()

        when:
        file('inputs/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.removed = ['file2.txt']")
    }

    def "incremental task is informed of 'out-of-date' files when all input files removed"() {
        given:
        previousExecution()

        when:
        file('inputs/file0.txt').delete()
        file('inputs/file1.txt').delete()
        file('inputs/file2.txt').delete()

        then:
        executesWithIncrementalContext("ext.removed = ['file0.txt', 'file1.txt', 'file2.txt']")
    }

    def "incremental task is informed of 'out-of-date' files with added, removed and modified files"() {
        given:
        previousExecution()

        when:
        file('inputs/file1.txt') << "changed content"
        file('inputs/file2.txt').delete()
        file('inputs/file3.txt') << "new file 3"
        file('inputs/file4.txt') << "new file 4"

        then:
        executesWithIncrementalContext("""
ext.changed = ['file1.txt']
ext.removed = ['file2.txt']
ext.added = ['file3.txt', 'file4.txt']
""")
    }

    def "incremental task is informed of 'out-of-date' files when task has no declared outputs or properties"() {
        given:
        buildFile.text = buildFileBase
        buildFile << """
    task incremental(type: BaseIncrementalTask) {
        inputDir = project.mkdir('inputs')
    }
"""
        and:
        previousExecution()

        when:
        file('inputs/file3.txt') << "file3 content"

        then:
        executesWithIncrementalContext("ext.added = ['file3.txt']")
    }

    def "incremental task is informed that all input files are 'out-of-date' when input property has changed"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.prop = 'changed'"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when input file property has been added"() {
        given:
        file('new-input.txt').text = "new input file"
        previousExecution()

        when:
        buildFile << "incremental.inputs.file('new-input.txt')"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when input file property has been removed"() {
        given:
        buildFile << """                            
            if (file('new-input.txt').exists()) {
                incremental.inputs.file('new-input.txt')
            }
        """
        def toBeRemovedInputFile = file('new-input.txt')
        toBeRemovedInputFile.text = "to be removed input file"
        previousExecution()

        when:
        toBeRemovedInputFile.delete()

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when task class has changed"() {
        given:
        previousExecution()

        when:
        buildFile.text = buildFileBase
        buildFile << """
    class IncrementalTask2 extends BaseIncrementalTask {}
    task incremental(type: IncrementalTask2) {
        inputDir = project.mkdir('inputs')
    }
"""

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output directory is changed"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.outputDir = project.mkdir('new-outputs')"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output file has changed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt") << "further change"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when output file has been removed"() {
        given:
        previousExecution()

        when:
        file("outputs/file1.txt").delete()

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when all output files have been removed"() {
        given:
        previousExecution()

        when:
        file("outputs").deleteDir()

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when Task.upToDate() is false"() {
        given:
        previousExecution()

        when:
        buildFile << "incremental.outputs.upToDateWhen { false }"

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed that all input files are 'out-of-date' when gradle is executed with --rerun-tasks"() {
        given:
        previousExecution()

        when:
        executer.withArgument("--rerun-tasks")

        then:
        executesWithRebuildContext()
    }

    def "incremental task is informed of 'out-of-date' files since previous successful execution"() {
        given:
        previousExecution()

        and:
        file('inputs/file1.txt') << "changed content"

        when:
        failedExecution()

        then:
        executesWithIncrementalContext("ext.changed = ['file1.txt']")
    }

    @Unroll
    @Issue("https://github.com/gradle/gradle/issues/4166")
    def "file in input dir appears in task inputs for #inputAnnotation"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @${inputAnnotation}
                File input
                @OutputFile
                File output
                
                @TaskAction
                void doStuff(IncrementalTaskInputs inputs) {
                    def out = []
                    inputs.outOfDate {
                        out << file.name
                    }
                    assert out.contains('child')
                    output.text = out.join('\\n')
                }
            }           
            
            task myTask(type: MyTask) {
                input = mkdir(inputDir)
                output = file("build/output.txt")
            }          
        """
        String myTask = ':myTask'

        when:
        file("inputDir1/child") << "inputFile1"
        run myTask, '-PinputDir=inputDir1'
        then:
        executedAndNotSkipped(myTask)

        when:
        file("inputDir2/child") << "inputFile2"
        run myTask, '-PinputDir=inputDir2'
        then:
        executedAndNotSkipped(myTask)

        where:
        inputAnnotation << [InputFiles.name, InputDirectory.name]
    }

    /*
     7. Sad-day cases
         - Incremental task has input files declared
         - Incremental task action throws exception
         - Incremental task action processes outOfDate files multiple times
         - Attempt to process removed files without first processing outOfDate files
     */

    def previousExecution() {
        run "incremental"
    }

    def failedExecution() {
        executer.withArgument("-PforceFail=yep")
        assert fails("incremental")
        executer.withArguments()
    }

    def executesWithIncrementalContext(String fileChanges) {
        buildFile << fileChanges
        succeeds "incrementalCheck"
    }

    def executesWithRebuildContext(String fileChanges = "") {
        buildFile << """
    ext.added = ['file0.txt', 'file1.txt', 'file2.txt', 'inputs']
    ext.incrementalExecution = false
"""
        buildFile << fileChanges
        succeeds "incrementalCheck"
    }
}
