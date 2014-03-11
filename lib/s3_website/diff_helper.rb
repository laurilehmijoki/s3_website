module S3Website
  class DiffHelper
    def self.resolve_files_to_upload(s3_bucket, site_dir, config)
      with_progress_indicator('Calculating diff') { |progress_indicator|
        s3_data_source = Filey::DataSources::AwsSdkS3.new(s3_bucket, config) { |filey|
          progress_indicator.render_next_step
        }
        fs_data_source = Filey::DataSources::FileSystem.new(site_dir) { |filey|
          if RUBY_PLATFORM =~ /darwin/
            filey.instance_variable_set(:@name, filey.name.force_encoding('UTF8-MAC').encode('UTF-8'))
            filey.instance_variable_set(:@path, filey.path.force_encoding('UTF8-MAC').encode('UTF-8'))
          end
          progress_indicator.render_next_step
        }
        changed_local_files = Filey::Comparison.list_changed(
          fs_data_source,
          s3_data_source
        )
        new_local_files = Filey::Comparison.list_missing(
          fs_data_source,
          s3_data_source
        )
        [
          reject_blacklisted(changed_local_files, config),
          reject_blacklisted(new_local_files, config)
        ]
      }
    end

    private

    def self.reject_blacklisted(file_paths, config)
      (normalise file_paths).reject { |f| Upload.is_blacklisted(f, config) }
    end

    def self.with_progress_indicator(diff_msg)
      progress_indicator = DiffProgressIndicator.new(diff_msg, "... done\n")
      result = yield progress_indicator
      progress_indicator.finish
      result
    end

    def self.normalise(fileys)
      fileys.map { |filey|
        filey.full_path.sub(/\.\//, '')
      }
    end

    class DiffProgressIndicator
      def initialize(init_msg, end_msg)
        @end_msg = end_msg
        @ordinal_direction = 'n' # start from north
        print init_msg
        print '   '
        render_next_step
      end

      def render_next_step
        @ordinal_direction = DiffProgressIndicator.next_ordinal_direction @ordinal_direction
        print("\b\b" + DiffProgressIndicator.render_ordinal_direction(@ordinal_direction) + ' ')
      end

      def finish
        print "\b\b"
        print @end_msg
      end

      private

      def self.render_ordinal_direction(ordinal_direction)
        case ordinal_direction
        when 'n'
          '|'
        when 'ne'
          '/'
        when 'e'
          '-'
        when 'se'
          '\\'
        when 's'
          '|'
        when 'sw'
          '/'
        when 'w'
          '-'
        when 'nw'
          '\\'
        else
          raise 'Unknown ordinal direction ' + ordinal_direction
        end
      end

      def self.next_ordinal_direction(current_ordinal_direction)
        case current_ordinal_direction
        when 'n'
          'ne'
        when 'ne'
          'e'
        when 'e'
          'se'
        when 'se'
          's'
        when 's'
          'sw'
        when 'sw'
          'w'
        when 'w'
          'nw'
        when 'nw'
          'n'
        else
          raise 'Unknown ordinal direction ' + current_ordinal_direction
        end
      end
    end
  end
end
