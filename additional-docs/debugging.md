# Tips for debugging

## Debugging with source code

First, clone the git repository:

    git clone https://github.com/laurilehmijoki/s3_website.git /tmp/s3_website

Next, edit a source file.

For example, you can change the AWS logging level in the
[src/main/resources/log4j.properties](https://github.com/laurilehmijoki/s3_website/blob/master/src/main/resources/log4j.properties) file. See [AWS SDK for Java docs](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-logging.html).

Another example: modify a `.scala` file by adding a `print()` statement into a
relevant location.

Then push your website with the cloned code:

    cd YOUR_WEBSITE_DIR
    /tmp/s3_website/bin/s3_website push

