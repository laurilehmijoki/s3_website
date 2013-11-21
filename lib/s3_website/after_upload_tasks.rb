module S3Website
class AfterUploadTasks
	def self.ping_sitemaps(config_file_dir)
		require 'net/http'
		require 'uri'
		config = S3Website::ConfigLoader.load_configuration config_file_dir
		puts "Pinging Sitemap to Google + Bing"
		puts "Your Sitemap.xml is located at: "+URI.escape(config['url'] +config['sitemap'])
		Net::HTTP.get(
			'www.google.com',
			'/webmasters/tools/ping?sitemap=' +
			URI.escape(config['url'] +config['sitemap'])
		)
		Net::HTTP.get(
			'www.bing.com',
			'/ping?sitemap=' +
			URI.escape(config['url'] +config['sitemap'])
		)
		puts "Pinged Sitemap servers!"
    rescue S3WebsiteError => e
      puts e.message
      exit 1
    end
end
end