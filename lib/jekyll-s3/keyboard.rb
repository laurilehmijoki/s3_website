module Jekyll
  module S3
    class Keyboard
      def self.if_user_confirms_delete(to_delete, standard_input=STDIN)
        delete_all = false
        keep_all = false
        to_delete.each do |f|
          delete = false
          keep = false
          until delete || delete_all || keep || keep_all
            puts "#{f} is on S3 but not in your _site directory anymore. Do you want to [d]elete, [D]elete all, [k]eep, [K]eep all?"
            case standard_input.gets.chomp
            when 'd' then delete = true
            when 'D' then delete_all = true
            when 'k' then keep = true
            when 'K' then keep_all = true
            end
          end
          if (delete_all || delete) && !(keep_all || keep)
            yield f
          end
        end
      end
    end
  end
end
