package com.calipso.smstemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, Long> {

    List<SmsTemplate> findByProfileIdAndActiveTrue(Long profileId);
}