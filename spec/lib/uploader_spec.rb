require 'spec_helper'

describe Jekyll::S3::Uploader do
  context '#upload_file' do
  end

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
end
