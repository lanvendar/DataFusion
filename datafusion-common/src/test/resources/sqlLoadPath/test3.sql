#sql(id="test3-key")
select * from xxx where id = '#(id)' and age > #(age) and day = #day(day,'-2M', 'MD', 'yyyy-MM-dd')
#end