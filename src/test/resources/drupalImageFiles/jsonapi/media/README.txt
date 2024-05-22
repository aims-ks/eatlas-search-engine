Download the index page using wget:
$ wget "http://domain.com/jsonapi/media/image?sort=-changed&page%5Blimit%5D=50&page%5Boffset%5D=0&filter%5Bstatus%5D=1" -O image_sort_-changed_page_5Blimit_5D_50_page_5Boffset_5D_0_filter_5Bstatus_5D_1

Download the image pages:
$ wget "http://domain.com/jsonapi/media/image/eae1f7db-bf10-4be5-957d-ece17b2b1ae7?include=thumbnail&filter%5Bstatus%5D=1" -O image/eae1f7db-bf10-4be5-957d-ece17b2b1ae7_include_thumbnail_filter_5Bstatus_5D_1
$ wget "http://domain.com/jsonapi/media/image/47ce7ed4-bb0b-4dcb-9f3a-8b9c41917dd4?include=thumbnail&filter%5Bstatus%5D=1" -O image/47ce7ed4-bb0b-4dcb-9f3a-8b9c41917dd4_include_thumbnail_filter_5Bstatus_5D_1
$ wget "http://domain.com/jsonapi/media/image/7b99cf2d-1539-413f-8e97-fc3e91610367?include=thumbnail&filter%5Bstatus%5D=1" -O image/7b99cf2d-1539-413f-8e97-fc3e91610367_include_thumbnail_filter_5Bstatus_5D_1
$ wget "http://domain.com/jsonapi/media/image/f4c8e050-b15b-424a-a90b-bca294922f9e?include=thumbnail&filter%5Bstatus%5D=1" -O image/f4c8e050-b15b-424a-a90b-bca294922f9e_include_thumbnail_filter_5Bstatus_5D_1
$ wget "http://domain.com/jsonapi/media/image/e3727cb7-0a17-465d-8dd7-cac5d2b10e47?include=thumbnail&filter%5Bstatus%5D=1" -O image/e3727cb7-0a17-465d-8dd7-cac5d2b10e47_include_thumbnail_filter_5Bstatus_5D_1
