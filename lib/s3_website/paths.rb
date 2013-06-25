module S3Website
  class Paths
    def self.site_paths
      [Nanoc::SITE_PATH, Jekyll::SITE_PATH]
    end

    def self.infer_site_path(candidate_path, pwd = Dir.pwd)
      if candidate_path == 'infer automatically'
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
          raise NoPredefinedWebsiteDirectoryFound
        end
      else
        raise NoWebsiteDirectoryFound.new(
          "Can't find a website in " + candidate_path
        ) unless File.exists? candidate_path
        candidate_path
      end
    rescue NoWebsiteDirectoryFound => e
      puts e.message
      exit 1
    end
  end
end
