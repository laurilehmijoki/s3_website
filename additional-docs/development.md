## Coding

Install a Scala editor. Intellij IDEA has great Scala support.

If you use IDEA, install the [Grep
Console](http://plugins.jetbrains.com/plugin/?idea&pluginId=7125) plugin. It
shows the ANSI colors in your IDEA console.

### Test runs with IDEA

1. Create a run profile: *Run* –> *Edit Configurations...*.
2. Add *Application*
3. Set *Main class* to `s3.website.Push`
4. Set *Program arguments* to `--site=/Users/you/yourtestsite/_site --config-dir=/Users/you/yourtestsite --verbose`

## Automated tests

    ./sbt test

## Test Linux distributions

Use Vagrant for testing the installation procedure on Linux.

Here's howto:

1. Install <https://www.vagrantup.com/downloads.html>
2. `cd vagrant && vagrant status`
3. launch with `vagrant up <name>` and ssh into with `vagrant ssh <name>`
4. test the latest release with `gem install s3_website && s3_website push`
