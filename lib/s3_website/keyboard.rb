module S3Website
  class Keyboard
    def self.if_user_confirms_delete(to_delete, config, standard_input=STDIN)
      delete_all = false
      keep_all = false
      confirmed_deletes = to_delete.map do |f|
        delete = false
        keep = false
        until delete || delete_all || keep || keep_all
          puts "#{f} is on S3 but not in your website directory anymore. Do you want to [d]elete, [D]elete all, [k]eep, [K]eep all?"
          case standard_input.gets.chomp
          when 'd' then delete = true
          when 'D' then delete_all = true
          when 'k' then keep = true
          when 'K' then keep_all = true
          end
        end
        if (delete_all || delete) && !(keep_all || keep)
          f
        end
      end.select { |f| f }
      Parallelism.each_in_parallel_or_sequentially(confirmed_deletes, config) { |f|
        yield f
      }
    end
  end
end
