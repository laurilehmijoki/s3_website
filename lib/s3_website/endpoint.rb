module S3Website
  class Endpoint
    DEFAULT_LOCATION_CONSTRAINT = 'us-east-1'
    attr_reader :region, :location_constraint, :hostname, :website_hostname

    def initialize(location_constraint=nil)
      location_constraint = DEFAULT_LOCATION_CONSTRAINT if location_constraint.nil?
      raise "Invalid S3 location constraint #{location_constraint}" unless
        location_constraints.has_key?location_constraint
      @region = location_constraints.fetch(location_constraint)[:region]
      @hostname = location_constraints.fetch(location_constraint)[:endpoint]
      @website_hostname = location_constraints.fetch(location_constraint)[:website_hostname]
      @location_constraint = location_constraint
    end

    # http://docs.amazonwebservices.com/general/latest/gr/rande.html#s3_region
    def location_constraints
      {
        'us-east-1'      => { :region => 'US Standard',                   :website_hostname => 's3-website-us-east-1.amazonaws.com',      :endpoint => 's3.amazonaws.com' },
        'us-west-2'      => { :region => 'US West (Oregon)',              :website_hostname => 's3-website-us-west-2.amazonaws.com',      :endpoint => 's3-us-west-2.amazonaws.com' },
        'us-west-1'      => { :region => 'US West (Northern California)', :website_hostname => 's3-website-us-west-1.amazonaws.com',      :endpoint => 's3-us-west-1.amazonaws.com' },
        'EU'             => { :region => 'EU (Ireland)',                  :website_hostname => 's3-website-eu-west-1.amazonaws.com',      :endpoint => 's3-eu-west-1.amazonaws.com' },
        'ap-southeast-1' => { :region => 'Asia Pacific (Singapore)',      :website_hostname => 's3-website-ap-southeast-1.amazonaws.com', :endpoint => 's3-ap-southeast-1.amazonaws.com' },
        'ap-southeast-2' => { :region => 'Asia Pacific (Sydney)',         :website_hostname => 's3-website-ap-southeast-2.amazonaws.com', :endpoint => 's3-ap-southeast-2.amazonaws.com' },
        'ap-northeast-1' => { :region => 'Asia Pacific (Tokyo)',          :website_hostname => 's3-website-ap-northeast-1.amazonaws.com', :endpoint => 's3-ap-northeast-1.amazonaws.com' },
        'sa-east-1'      => { :region => 'South America (Sao Paulo)',     :website_hostname => 's3-website-sa-east-1.amazonaws.com',      :endpoint => 's3-sa-east-1.amazonaws.com' }
      }
    end
  end
end
