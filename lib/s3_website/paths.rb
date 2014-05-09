module S3Website
  class Paths
    def self.site_paths
      [Nanoc::SITE_PATH, Jekyll::SITE_PATH]
    end

    def self.infer_site_path(candidate_path, pwd)
      if candidate_path == 'infer automatically'
        infer_automatically pwd
      else
        candidate_path_if_exists candidate_path
      end
    end

    private

    def self.candidate_path_if_exists(candidate_path)
      raise NoWebsiteDirectoryFound.new(
        "Can't find a website in " + candidate_path
      ) unless File.exists? candidate_path
      candidate_path
    end

    def self.infer_automatically(pwd)
      site_path = site_paths.
        map do |site_path|
        pwd + '/' + site_path
        end.
          find do |site_path|
          File.exists? site_path
          end
        if site_path
          site_path
        else
          puts "Could not find a website directory. Specify one with the --site parameter."
          exit
        end
    end
  end
end
