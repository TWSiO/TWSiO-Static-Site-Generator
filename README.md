# TWSiO Static Site Generator

The static generator that I created for my site [thiswebsiteis.online](https://thiswebsiteis.online). Made using [stasis](https://github.com/magnars/stasis).

It's not intended to be super reusable, it's really just a bunch of scripts cobbled together. I'm more just making my scripts available just for whoever wants to see them. Maybe there's something interesting in there to someone.

---

The actual content for the site is included in a directory called /resources that I have not included in this repository. The generator handles files in that directory differently based on which subdirectory they're in.

- /site/
    - Other than the blog, this creates the pages for the site by rendering either the mustache template or markdown file and putting it into the default template, and putting it at the corresponding place in the URL path that it's in, in the file system path.

- /site/blog/
    - Handles them as blog posts using the blog post template, putting them into the blog feed by order of their date.

- /templates/
    - Templates that are used to generate other pages or pages with lots of metadata.

- other
    - All other pages in the /resources/ directory are just copied over to the same place in the URL path as the file path as is.
