package com.discover.config;

import com.discover.mapper.CustomerMapper;
import com.discover.model.Customer;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.batch.item.file.FlatFileHeaderCallback;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.Writer;

@Configuration
public class BatchConfig {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CustomerMapper customerMapper;

    @Bean
    public Job importUserJob(JobRepository jobRepository, Step step1, Step step2) {
        return new JobBuilder("importCustomerJob", jobRepository)
                //.flow(step1)
                .start(step1).on("FAILED").end()
                .from(step1).on("*").to(step2)
                .end()
                .build();
    }

    @Bean
    public Step step1(JobRepository jobRepository, PlatformTransactionManager transactionManager)throws Exception {
        return new StepBuilder("step1", jobRepository)
                .<Customer, Customer> chunk(2, transactionManager)
                .reader(dbReader())
                .processor(processor())
                .writer(fileWriter())
                .build();
    }

    @Bean
    public Step step2(JobRepository jobRepository, PlatformTransactionManager transactionManager)throws Exception {
        return new StepBuilder("step2", jobRepository)
                .tasklet(taskletVerify(), transactionManager)
                .build();
    }



    @Bean
    public Tasklet taskletVerify() {
        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

                FileSystemResource resource = new FileSystemResource("data");
                File dir = resource.getFile();
                Assert.state(dir.isDirectory(), "The resource must be a directory");
                File[] files = dir.listFiles();
                Assert.state( files.length > 0 , "couldn't write db records");
                System.out.println("tasklet executed successfully!");
                return RepeatStatus.FINISHED;
            }
        };
    }
    @Bean
    @StepScope
    public ItemStreamReader<Customer> dbReader()
            throws Exception {
        return (ItemStreamReader<Customer>) itemStreamReader(customerMapper, "select * ",
                "from customer",
                "where 1 = 1'");
    }

    @StepScope
    public ItemStreamReader<? extends Object> itemStreamReader(RowMapper rowMapper, String select, String from, String where) throws Exception {
        JdbcPagingItemReader<Object> reader = new JdbcPagingItemReader<Object>();
        reader.setDataSource(dataSource);
        final SqlPagingQueryProviderFactoryBean sqlPagingQueryProviderFactoryBean = new SqlPagingQueryProviderFactoryBean();
        sqlPagingQueryProviderFactoryBean.setDataSource(dataSource);
        // sqlPagingQueryProviderFactoryBean.setDataSource(dataSource);
        sqlPagingQueryProviderFactoryBean.setSelectClause(select);
        sqlPagingQueryProviderFactoryBean.setFromClause(from);
        // sqlPagingQueryProviderFactoryBean.setWhereClause(where);
        sqlPagingQueryProviderFactoryBean.setSortKey("id");
        reader.setQueryProvider(sqlPagingQueryProviderFactoryBean.getObject());
        reader.setPageSize(100);
        reader.setRowMapper(rowMapper);
        reader.afterPropertiesSet();
        reader.setSaveState(false);
        return reader;
    }

    @Bean
    @StepScope
    public ItemProcessor<Customer, Customer> processor() {
        return new ItemProcessor<Customer, Customer>() {
            @Override
            public Customer process(Customer customer) throws Exception {
                return customer;
            }
        };
    }


    /*


     */
    @Bean
    @StepScope
    public FlatFileItemWriter<Customer> fileWriter() {
        FlatFileItemWriter<Customer> writer = new FlatFileItemWriter<Customer>();
        writer.setResource(new FileSystemResource("data/output.csv"));
        writer.setHeaderCallback(new FlatFileHeaderCallback() {
            @Override
            public void writeHeader(Writer writer) throws IOException {
                writer.write("id, first_name, last_name, email, phone, gender, age, registered, orders, spent, job, hobbies, is_married");
            }
        });
        DelimitedLineAggregator<Customer> lineAggregator = new DelimitedLineAggregator<Customer>();
        lineAggregator.setDelimiter(",");
        BeanWrapperFieldExtractor<Customer> fieldExtractor = new BeanWrapperFieldExtractor<Customer>();
        fieldExtractor.setNames(new String[]{"id", "first_name", "last_name", "email", "phone", "gender", "age", "registered", "orders", "spent","job","hobbies","is_married"});
        lineAggregator.setFieldExtractor(fieldExtractor);
        writer.setLineAggregator(lineAggregator);
        return writer;
    }

}
