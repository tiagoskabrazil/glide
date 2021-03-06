package glide.runner

import glide.runner.commnads.CreateAppCommand
import glide.runner.commnads.DevAppServerRunCommand
import glide.runner.commnads.GradleTaskCommand
import glide.runner.commnads.HelpCommand
import glide.runner.commnads.VersionCommand
import glide.runner.components.GlideRuntime
import glide.runner.components.OutputApp
import glide.runner.components.TemplateApp
import glide.runner.components.UserApp
import glide.runner.exceptions.HumanFriendlyExceptionHandler
import glide.runner.exceptions.InvalidGlideAppException

// todo -- implement -q (quiet setting)
// todo -- refactor and split

class Main {

    PrintWriter writer
    GlideCli cli
    AntBuilder ant = new AntBuilder()

    Main(PrintWriter writer) {
        this.writer = writer
        this.cli = new GlideCli(writer)
    }

    def run(String[] args) {
        OptionAccessor options = cli.parse(args)

        def command = (options.arguments() ? options.arguments().first() : 'run')
        if (options.h) command = "help"
        if (options.v) command = "version"

        switch (command) {
            case ['help']: new HelpCommand(this.cli).execute(); break
            case ['version']: new VersionCommand(this.cli).execute(); break
            case ['native', 'lite']: new DevAppServerRunCommand(prepareRuntime(options), ant).execute(); break
            case ['new', 'create']: new CreateAppCommand(ant, options).execute(); break
            case ['run', 'start']: new GradleTaskCommand(prepareRuntime(options), ant, "appengineRun").execute(); break
            case ['clean']: ant.delete(dir:prepareRuntime(options).outputApp.dir); break
            case ['idea']: generateIdeaProject(options); break
            case ['deploy', 'upload']: new GradleTaskCommand(prepareRuntime(options), ant, "appengineUpdate").execute(); break
            case ['test']: new GradleTaskCommand(prepareRuntime(options), ant, "test").execute(); break
            case ['export']: new GradleTaskCommand(prepareRuntime(options), ant, "wrapper").execute(); break
            default: new GradleTaskCommand(prepareRuntime(options), ant, command).execute(); break
        }
    }

    private def generateIdeaProject(options) {
        def runtime = prepareRuntime(options)
        new GradleTaskCommand(runtime, ant, "idea").execute()
        ant.copy(toDir:runtime.userApp.dir) {
            fileset(dir:runtime.outputApp.dir, includes:"*.iml, *.ipr")
        }
        println "copied"
    }

    // read the optional values (flags)
    private GlideRuntime prepareRuntime(OptionAccessor options) {
        def glideHome = System.env.GLIDE_HOME
        validateGlideHome(options, glideHome)

        def configSlurper = options.e ? new ConfigSlurper(options.e) : new ConfigSlurper()
        def userApp = new UserApp(options.a ?: System.getProperty("user.dir"), configSlurper)
        def templateApp = new TemplateApp(options.t ?: "${glideHome}/base-templates/gae-base-web", configSlurper)
        def outputApp = new OutputApp(options.o ?: "${System.getProperty("java.io.tmpdir")}/glide-generated/${userApp.glideConfig.app.name}")

        if (!userApp.validate()) {
            throw new InvalidGlideAppException("A valid Glide app does not exist. Use `glide create` to create one.")
        }
        new GlideRuntime(userApp: userApp, templateApp: templateApp, outputApp: outputApp) // form the app
    }

    private void validateGlideHome(options, glideHome) {
        if (!options.t && !glideHome)
            throw new IllegalStateException("Environment variable GLIDE_HOME is not set.")
        else if (glideHome && !new File(glideHome).exists())
            throw new IllegalStateException("Directory specified by environment variable GLIDE_HOME does not exist.")
    }

    public static void main(String[] args) {

        HumanFriendlyExceptionHandler.wrap {
            def writer = new PrintWriter(System.out, true) // auto-flushing writer
            new Main(writer).run(args)
        }

        System.exit(0) // all is well
    }
}
