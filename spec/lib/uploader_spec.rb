require 'spec_helper'

describe Jekyll::S3::Uploader do
  context '#load_all_local_files' do
    let(:files) {
      Jekyll::S3::Uploader.send(:load_all_local_files,
                                'spec/sample_files/hyde_site/_site')
    }

    it 'loads regular files' do
      files.should include('css/styles.css')
      files.should include('index.html')
    end

    it 'loads also dotfiles' do
      files.should include('.vimrc')
    end
  end

  context "#build_list_of_files_to_delete" do
    it "ignores files which match a regular expression" do
      files_to_delete = Jekyll::S3::Uploader.build_list_of_files_to_delete(["a", "b", "ignored"], ["a"], "ignored")
      files_to_delete.should eq ["b"]
    end
    it "does not ignore when you don't provide an ignored regex" do
      files_to_delete = Jekyll::S3::Uploader.build_list_of_files_to_delete(["a", "b", "ignored"], ["a"])
      files_to_delete.should eq ["b", "ignored"]
    end
  end
end
