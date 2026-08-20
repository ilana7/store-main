package com.example.store.service;

import com.example.store.entity.Customer;
import com.example.store.repository.CustomerRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTests {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private String capturePattern(String query) {
        clearInvocations(customerRepository);
        when(customerRepository.searchByName(anyString())).thenReturn(List.of());
        customerService.search(query);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(customerRepository).searchByName(captor.capture());
        return captor.getValue();
    }

    @Test
    void blankQueryReturnsEveryCustomer() {
        when(customerRepository.findAll()).thenReturn(List.of(new Customer()));

        assertThat(customerService.search("   ")).hasSize(1);
        verify(customerRepository).findAll();
        verify(customerRepository, never()).searchByName(anyString());
    }

    @Test
    void nullQueryReturnsEveryCustomer() {
        when(customerRepository.findAll()).thenReturn(List.of(new Customer()));

        assertThat(customerService.search(null)).hasSize(1);
        verify(customerRepository).findAll();
    }

    @Test
    void queryBecomesLowercaseContainsPattern() {
        assertThat(capturePattern("DoE")).isEqualTo("%doe%");
    }

    @Test
    void wildcardsInTheQueryAreEscapedRatherThanHonoured() {
        // Without escaping, "%" would match every customer instead of a literal percent sign.
        assertThat(capturePattern("%")).isEqualTo("%!%%");
        assertThat(capturePattern("_")).isEqualTo("%!_%");
        assertThat(capturePattern("!")).isEqualTo("%!!%");
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertThat(capturePattern("  doe  ")).isEqualTo("%doe%");
    }
}
