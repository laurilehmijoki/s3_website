module Jekyll
  module S3
    class DiffHelper
      def self.resolve_files_to_upload(s3_bucket, site_dir)
        s3_data_source = Filey::DataSources::AwsSdkS3.new(s3_bucket)
        fs_data_source = Filey::DataSources::FileSystem.new(site_dir)
        changed_local_files =
          Filey::Comparison.list_changed(fs_data_source, s3_data_source)
        new_local_files =
          Filey::Comparison.list_missing(fs_data_source, s3_data_source)
        [ normalise(changed_local_files), normalise(new_local_files) ]
      end

      private

      def self.normalise(fileys)
        fileys.map { |filey|
          filey.full_path.sub(/\.\//, '')
        }
      end
    end
  end
end
