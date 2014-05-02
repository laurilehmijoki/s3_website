package s3.website

import s3.website.model.{Site, Config}

object Implicits {
  implicit def site2Config(implicit site: Site): Config = site.config
}
