package dev.jackteng.portfolio.catalog;

public record PageRequest(int page,int pageSize) {
    public static PageRequest parse(String page,String size) {
        if(!page.matches("[1-9][0-9]{0,5}") || !size.matches("[1-9][0-9]?") || Integer.parseInt(page)>100000 || Integer.parseInt(size)>50)
            throw new CatalogProblem(400,"INVALID_QUERY","分頁條件不正確");
        return new PageRequest(Integer.parseInt(page),Integer.parseInt(size));
    }
    public int offset(){return (page-1)*pageSize;}
    public int pages(int total){return (int)((total+(long)pageSize-1)/pageSize);}
}
