# Running from an EC2 instance with jekyll files on Dropbox

Based on [this](http://namelesshorror.com/2015/02/26/jekyll-dropbox-aws-and-ifttt-easy-blogging/) article about automating the deployment of jekyll via an EC2 instance, I wrote the following shell script for use with such a setup. It assumes that you have a Linux Amazon EC2 instance, with the Dropbox client running as a daemon, and s3_website installed and configured. The script could be run from the default user's cron every so often, and allow anyone to effectively serve their jekyll source files to AWS from Dropbox.


[jekyll-s3-dropbox.sh](https://gist.github.com/RNCTX/359489a5432937578bf5736850917d70)
