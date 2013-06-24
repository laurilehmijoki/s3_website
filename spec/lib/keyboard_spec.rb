require 'spec_helper'

describe S3Website::Keyboard do
  describe '.keep_or_delete' do
    let(:s3_object_keys) { ['a', 'b', 'c'] }
    let(:standard_input) { stub('std_in') }

    it 'can delete only the first item' do
      standard_input.stub(:gets).and_return("d", "K")
      deleted_keys = call_keyboard(s3_object_keys, standard_input)
      deleted_keys.should eq(['a'])
    end

    it 'can delete only the second item' do
      standard_input.stub(:gets).and_return("k", "d", "k")
      deleted_keys = call_keyboard(s3_object_keys, standard_input)
      deleted_keys.should eq(['b'])
    end

    it 'can delete all but the first item' do
      standard_input.stub(:gets).and_return("k", "D")
      deleted_keys = call_keyboard(s3_object_keys, standard_input)
      deleted_keys.should eq(['b', 'c'])
    end

    it 'can delete all s3 objects' do
      standard_input.stub(:gets).and_return("D")
      deleted_keys = call_keyboard(s3_object_keys, standard_input)
      deleted_keys.should eq(['a', 'b', 'c'])
    end

    it 'can keep one s3 object' do
      standard_input.stub(:gets).and_return("k", "d", "d")
      deleted_keys = call_keyboard(s3_object_keys, standard_input)
      deleted_keys.should eq(['b', 'c'])
    end

    it 'can keep all s3 objects' do
      standard_input.stub(:gets).and_return("k", "k", "k")
      deleted_keys = call_keyboard(s3_object_keys, standard_input)
      deleted_keys.should eq([])
    end

    it 'can keep all s3 objects' do
      standard_input.stub(:gets).and_return("K")
      deleted_keys = call_keyboard(s3_object_keys, standard_input)
      deleted_keys.should eq([])
    end

    def call_keyboard(s3_object_keys, standard_input)
      deleted_keys = []
      S3Website::Keyboard.if_user_confirms_delete(s3_object_keys, 
                                                   standard_input) { |key|
        deleted_keys << key
      }
      deleted_keys
    end
  end
end
