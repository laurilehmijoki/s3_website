require 'zlib'
require 'zopfli'

module S3Website
  class GzipHelper
    def initialize(config, site_dir)
      @config = config
      @site_dir = site_dir
      @extensions =
        if config['gzip']
          config['gzip'].is_a?(Array) ? config['gzip'] : S3Website::DEFAULT_GZIP_EXTENSIONS
        else
          []
        end
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
      cont = IO.binread(filename)
      if @config['gzip_zopfli']
        gz_data = Zopfli.deflate cont, format: :gzip
        IO.binwrite(filename, gz_data)
      else
        cont = IO.binread(filename)
        Zlib::GzipWriter.open(filename, Zlib::BEST_COMPRESSION, Zlib::DEFAULT_STRATEGY) do |gz|
          # Set mtime to a fake value to ensure that it's always the same. Note that non-gzipped
          # files are compared using only MD5 based on content, mtime isn't taken into account.
          # Also zopfli doesn't include mtime.
          gz.mtime = 1
          gz.orig_name = File.basename(filename)
          gz.write cont
        end
      end
    end
  end
end
