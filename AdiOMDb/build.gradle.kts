// AdiOMDb/build.gradle.kts

// use an integer for version numbers
version = 1


cloudstream {
    // Properti ID unik ini WAJIB ADA dan harus cocok dengan package name Anda
    id = "com.AdiOMDb" 

    // All of these properties are optional, you can safely remove them

    description = "AdiOMDb (OMDb)" 
    language    = "en" 
    authors = listOf("AdiOMDb") 

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 

    // List of video source types.
    tvTypes = listOf("Movie","TvSeries","Anime","AsianDrama")

    iconUrl="https://www.google.com/s2/favicons?domain=moviebox.ph&sz=%size%"

    isCrossPlatform = true
}
