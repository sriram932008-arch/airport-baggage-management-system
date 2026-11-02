/**
 * API Client for Intelligent Airport Baggage Handling and Routing System
 */
const API = {
  baseUrl: '/api',

  getToken() {
    return localStorage.getItem('airport_session_token') || sessionStorage.getItem('airport_session_token');
  },

  setToken(token, remember = false) {
    if (remember) {
      localStorage.setItem('airport_session_token', token);
    } else {
      sessionStorage.setItem('airport_session_token', token);
    }
  },

  clearToken() {
    localStorage.removeItem('airport_session_token');
    sessionStorage.removeItem('airport_session_token');
  },

  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers
    };

    const token = this.getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
      headers['X-Session-Token'] = token;
    }

    const config = {
      ...options,
      headers
    };

    try {
      const response = await fetch(url, config);
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        const errorMsg = data && data.error ? data.error : `HTTP error ${response.status}: ${response.statusText}`;
        const err = new Error(errorMsg);
        err.status = response.status;
        err.data = data;
        throw err;
      }

      return data;
    } catch (err) {
      console.error(`API Error [${endpoint}]:`, err);
      throw err;
    }
  },

  // Auth Endpoints
  login(identifier, password, role) {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ identifier, password, role })
    });
  },

  register(userData) {
    const payload = { ...userData };
    const baggageDetails = Array.isArray(payload.baggageDetails) ? payload.baggageDetails : [];
    delete payload.baggageDetails;
    baggageDetails.forEach((detail, index) => {
      const position = index + 1;
      payload[`baggageName${position}`] = detail.name;
      payload[`baggageType${position}`] = detail.type;
      payload[`baggageFlight${position}`] = detail.flightNumber;
    });
    return this.request('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  logout() {
    const p = this.request('/auth/logout', { method: 'POST' }).catch(() => {});
    this.clearToken();
    return p;
  },

  getCurrentUser() {
    return this.request('/auth/me');
  },

  // Forgot Password
  forgotPassword(identifier, phoneNumber) {
    return this.request('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ identifier, phoneNumber })
    });
  },

  resetPassword(identifier, otp, newPassword, confirmPassword) {
    return this.request('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ identifier, otp, newPassword, confirmPassword })
    });
  },

  // Public Endpoints
  publicTrack(id) {
    return this.request(`/public/track?id=${encodeURIComponent(id)}`);
  },

  publicFlights() {
    return this.request('/public/flights');
  },

  // Passenger Endpoints
  getPassengerBaggage() {
    return this.request('/passenger/baggage');
  },

  trackPassengerBag(id) {
    return this.request(`/passenger/track?id=${encodeURIComponent(id)}`);
  },

  // Admin Endpoints
  getDashboardStats() {
    return this.request('/admin/dashboard-stats');
  },

  getAdminBaggage(filters = {}) {
    const q = new URLSearchParams();
    if (filters.search) q.append('search', filters.search);
    if (filters.flight && filters.flight !== 'ALL') q.append('flight', filters.flight);
    if (filters.status && filters.status !== 'ALL') q.append('status', filters.status);
    return this.request(`/admin/baggage?${q.toString()}`);
  },

  setClearance(baggageId, cleared) {
    return this.request('/admin/baggage/clearance', {
      method: 'POST',
      body: JSON.stringify({ baggageId, cleared: String(cleared) })
    });
  },

  updateBaggageStatus(baggageId, status, location) {
    return this.request('/admin/baggage/status', {
      method: 'POST',
      body: JSON.stringify({ baggageId, status, location })
    });
  },

  registerBaggage(bagData) {
    return this.request('/admin/baggage/add', {
      method: 'POST',
      body: JSON.stringify(bagData)
    });
  },

  getFlights(search = '') {
    const query = search ? `?search=${encodeURIComponent(search)}` : '';
    return this.request(`/admin/flights${query}`);
  },

  addFlight(flight) {
    return this.request('/admin/flights/add', {
      method: 'POST',
      body: JSON.stringify({ ...flight, confirmed: true })
    });
  },

  updateFlightSchedule(schedule) {
    return this.request('/admin/flights/schedule', {
      method: 'POST',
      body: JSON.stringify({ ...schedule, confirmed: true })
    });
  },

  updateFlightBay(flightNumber, newBayId) {
    return this.request('/admin/flights/bay', {
      method: 'POST',
      body: JSON.stringify({ flightNumber, newBayId })
    });
  },

  updateFlightCutoff(flightNumber, cutoffMinutes, cutoffTime) {
    return this.request('/admin/flights/cutoff', {
      method: 'POST',
      body: JSON.stringify({ flightNumber, cutoffMinutes: String(cutoffMinutes), cutoffTime })
    });
  },

  getConveyors() {
    return this.request('/admin/conveyors');
  },

  toggleConveyor(segmentId, active) {
    return this.request('/admin/conveyors/toggle', {
      method: 'POST',
      body: JSON.stringify({ segmentId, active: String(active) })
    });
  },

  getLoadingBays() {
    return this.request('/admin/loading-bays');
  },

  calculateRoute(baggageId) {
    return this.request('/admin/route/calculate', {
      method: 'POST',
      body: JSON.stringify({ baggageId })
    });
  },

  recalculateAllRoutes() {
    return this.request('/admin/route/recalculate-all', {
      method: 'POST'
    });
  },

  getReports() {
    return this.request('/admin/reports');
  },

  runTestScenarios() {
    return this.request('/test/run-scenarios', {
      method: 'POST'
    });
  },

  // ==================== PRIORITY QUEUE ====================
  getPriorityQueue() {
    return this.request('/admin/baggage/priority-queue');
  },

  getMyPriorityStatus() {
    return this.request('/passenger/priority-status');
  },

  // ==================== COMPLAINTS ====================
  submitComplaint(data) {
    return this.request('/passenger/complaints', {
      method: 'POST',
      body: JSON.stringify(data)
    });
  },

  getMyComplaints() {
    return this.request('/passenger/complaints');
  },

  getAdminComplaints(statusFilter) {
    const q = statusFilter && statusFilter !== 'ALL' ? `?status=${encodeURIComponent(statusFilter)}` : '';
    return this.request(`/admin/complaints${q}`);
  },

  updateComplaint(complaintId, status, adminResponse) {
    return this.request('/admin/complaints/update', {
      method: 'POST',
      body: JSON.stringify({ complaintId, status, adminResponse })
    });
  },

  getComplaintPendingCount() {
    return this.request('/admin/complaints/pending-count');
  }
};
