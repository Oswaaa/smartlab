package com.smartlab.management.controller.resource.adapter;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.resource.adapter.VirtualLeaseRequest;
import com.smartlab.management.entity.resource.adapter.VirtualLease;
import com.smartlab.management.service.db.resource.adapter.VirtualLeaseService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirtualLeaseControllerTest {

    @Test
    void listsByPhysicalInstance() {
        VirtualLeaseService service = mock(VirtualLeaseService.class);
        VirtualLease lease = new VirtualLease();
        lease.setId(8L);
        when(service.list(3L, null, null)).thenReturn(List.of(lease));
        VirtualLeaseController controller = new VirtualLeaseController(service);

        ApiResponse<List<VirtualLease>> response = controller.list(3L, null, null);

        assertTrue(response.isSuccess());
        assertEquals(8L, response.getData().get(0).getId());
    }

    @Test
    void requestForwardsPhysicalInstanceAndTask() {
        VirtualLeaseService service = mock(VirtualLeaseService.class);
        VirtualLease lease = new VirtualLease();
        lease.setId(11L);
        when(service.beginLease(3L, 9L)).thenReturn(lease);
        VirtualLeaseController controller = new VirtualLeaseController(service);

        ApiResponse<VirtualLease> response = controller.request(new VirtualLeaseRequest(3L, 9L));

        assertTrue(response.isSuccess());
        assertEquals(11L, response.getData().getId());
        verify(service).beginLease(3L, 9L);
    }

    @Test
    void releaseForwardsLeaseId() {
        VirtualLeaseService service = mock(VirtualLeaseService.class);
        VirtualLease lease = new VirtualLease();
        lease.setId(11L);
        when(service.beginRelease(11L)).thenReturn(lease);
        VirtualLeaseController controller = new VirtualLeaseController(service);

        ApiResponse<VirtualLease> response = controller.release(11L);

        assertTrue(response.isSuccess());
        verify(service).beginRelease(11L);
    }
}
