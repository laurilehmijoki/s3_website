require 'zlib'
require 'zopfli'

module S3Website
  class GzipHelper
    def initialize(config, site_dir)
      @config = config
      @site_dir = site_dir
      @extensions = config['gzip'].is_a?(Array) ? config['gzip'] : S3Website::DEFAULT_GZIP_EXTENSIONS
    end

    def gzip_files
      Dir.glob(@site_dir + '/**/{*,.*}', File::FNM_DOTMATCH).each { |file|
        gzip_file!(file) if File.file?(file) && gzip?(file)
      }
    end

    private

    def gzip?(filename)
      @extensions.include?(File.extname(filename))
    end

    def gzip_file!(filename)
      File.open(filename, 'r+') do |f|
        cont = f.read
        f.rewind
        if @config['gzip_zopfli']
          gz_data = Zopfli.deflate cont, format: :gzip
          f.write(gz_data)
          f.flush
        else
          gz = Zlib::GzipWriter.new(f, Zlib::BEST_COMPRESSION, Zlib::DEFAULT_STRATEGY)
          # Set mtime to a fake value to ensure that it's always the same. Note that non-gzipped
          # files are compared using only MD5 based on content, mtime isn't taken into account.
          gz.mtime = 1
          gz.orig_name = File.basename(filename)
          gz.write(cont)
          gz.flush
          f.flush
          gz.close
        end
      end
    end
  end
end
