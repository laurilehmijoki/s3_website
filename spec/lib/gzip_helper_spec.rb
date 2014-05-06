require 'spec_helper'

describe S3Website::GzipHelper do
  describe 'gzip compression' do
    let(:config) {
      {
        's3_reduced_redundancy' => false,
        'gzip' => true
      }
    }
    let(:test_site_dir) { 'features/support/test_site_dirs/my.blog.com/_site' }

    describe '#gzip_files' do
      it 'should gzip all files that match default gzip extensions (e.g. html, css, js) [gzip]' do
        gzip_all_test
      end

      it 'should gzip all files that match default gzip extensions (e.g. html, css, js) [gzip_zopfli]' do
        config['gzip_zopfli'] = true
        gzip_all_test
      end

      it 'should gzip all files that match custom gzip extensions [gzip]' do
        gzip_html_test
      end

      it 'should gzip all files that match custom gzip extensions [gzip_zopfli]' do
        config['gzip_zopfli'] = true
        gzip_html_test
      end

      it 'should not gzip anything if the config does not specify gzip [gzip]' do
        gzip_nothing_test
      end

      it 'should not gzip anything if the config does not specify gzip [gzip_zopfli]' do
        config['gzip_zopfli'] = true
        gzip_nothing_test
      end
    end
  end

  def gzip_all_test
    in_tmp_dir (test_site_dir) do |tmpdir|
      orig_content = [
        File.read("#{tmpdir}/index.html"),
        File.read("#{tmpdir}/css/styles.css")
      ]
      S3Website::GzipHelper.new(config, tmpdir).gzip_files
      expected_content = [
        Zlib::GzipReader.open("#{tmpdir}/index.html") {|gz| gz.read },
        Zlib::GzipReader.open("#{tmpdir}/css/styles.css") {|gz| gz.read }
      ]
      expected_content.should == orig_content
    end
  end

  def gzip_html_test
    config['gzip'] = %w(.html)
    in_tmp_dir (test_site_dir) do |tmpdir|
      orig_content = [
        File.read("#{tmpdir}/index.html"),
        File.read("#{tmpdir}/css/styles.css")
      ]
      S3Website::GzipHelper.new(config, tmpdir).gzip_files
      expected_content = [
        Zlib::GzipReader.open("#{tmpdir}/index.html") {|gz| gz.read },
        File.read("#{tmpdir}/css/styles.css")
      ]
      expected_content.should == orig_content
    end
  end

  def gzip_nothing_test
    config.delete 'gzip'
    in_tmp_dir (test_site_dir) do |tmpdir|
      orig_content = [
        File.read("#{tmpdir}/index.html"),
        File.read("#{tmpdir}/css/styles.css")
      ]
      S3Website::GzipHelper.new(config, tmpdir).gzip_files
      expected_content = [
        File.read("#{tmpdir}/index.html"),
        File.read("#{tmpdir}/css/styles.css")
      ]
      expected_content.should == orig_content
    end
  end

  def in_tmp_dir (dir)
    Dir.mktmpdir do |tmpdir|
      FileUtils.cp_r(dir, tmpdir)
      yield File.join(tmpdir, File.basename(dir))
    end
  end
end
