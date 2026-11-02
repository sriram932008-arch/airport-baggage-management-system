/**
 * Application Controller for Intelligent Airport Baggage Handling and Routing System
 */

const App = {
  currentUser: null,
  currentPassenger: null,
  activeView: 'welcome',
  adminTab: 'overview',
  cachedFlights: [],
  cachedFlightBaggage: [],
  cachedConveyors: [],
  cachedBaggage: [],
  selectedRoutingBag: null,
  cachedComplaints: [],
  cachedPassengerBaggage: [],
  cachedRegistrationFlights: [],
  selectedComplaint: null,

  init() {
    this.bindEvents();
    this.loadRegistrationFlights();
    this.checkInitialSession();
  },

  async loadRegistrationFlights() {
    try {
      this.cachedRegistrationFlights = await API.publicFlights() || [];
    } catch (err) {
      this.cachedRegistrationFlights = [];
      console.error('Unable to load registration flight options:', err);
    }
    this.renderRegistrationBaggageFields();
  },

  renderRegistrationBaggageFields(countInputId = 'welcome-reg-baggage-count',
                                  containerId = 'welcome-reg-baggage-details',
                                  idPrefix = 'welcome-reg-bag') {
    const countInput = document.getElementById(countInputId);
    const container = document.getElementById(containerId);
    if (!countInput || !container) return;
    const previousDetails = [];
    for (let index = 1; index <= 10; index++) {
      const name = document.getElementById(`${idPrefix}-name-${index}`);
      if (!name) break;
      previousDetails.push({
        name: name.value,
        type: document.getElementById(`${idPrefix}-type-${index}`)?.value,
        flightNumber: document.getElementById(`${idPrefix}-flight-${index}`)?.value
      });
    }
    const count = Number.parseInt(countInput.value, 10);
    if (!Number.isInteger(count) || count < 1 || count > 10) {
      container.innerHTML = '<p class="form-error">Choose between 1 and 10 baggage units.</p>';
      return;
    }
    const flightOptions = this.cachedRegistrationFlights.map(flight =>
      `<option value="${this.escapeHtml(flight.flightNumber)}">${this.escapeHtml(flight.flightNumber)} — ${this.escapeHtml(flight.destination)}</option>`
    ).join('');
    container.innerHTML = `<h3 style="font-size:1rem;">Baggage details</h3>` + Array.from({ length: count }, (_, index) => `
      <fieldset style="border:1px solid var(--border);border-radius:var(--radius-md);padding:1rem;margin:0 0 1rem;">
        <legend style="padding:0 0.4rem;font-weight:600;">Baggage ${index + 1}</legend>
        <div class="form-group">
          <label class="form-label" for="${idPrefix}-name-${index + 1}">Baggage name or description</label>
          <input id="${idPrefix}-name-${index + 1}" class="form-input" maxlength="80" placeholder="e.g. Blue suitcase" required>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label" for="${idPrefix}-type-${index + 1}">Baggage type</label>
            <select id="${idPrefix}-type-${index + 1}" class="form-input" required>
              <option value="ORIGIN">Checked baggage</option>
              <option value="TRANSFER">Transfer baggage</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label" for="${idPrefix}-flight-${index + 1}">Flight</label>
            <select id="${idPrefix}-flight-${index + 1}" class="form-input" required ${flightOptions ? '' : 'disabled'}>
              <option value="">${flightOptions ? 'Select your flight' : 'Flight list unavailable'}</option>
              ${flightOptions}
            </select>
          </div>
        </div>
        <small>Baggage ID will be generated after registration.</small>
      </fieldset>
    `).join('');
    previousDetails.slice(0, count).forEach((detail, index) => {
      const position = index + 1;
      const name = document.getElementById(`${idPrefix}-name-${position}`);
      const type = document.getElementById(`${idPrefix}-type-${position}`);
      const flight = document.getElementById(`${idPrefix}-flight-${position}`);
      if (name) name.value = detail.name;
      if (type) type.value = detail.type;
      if (flight && this.cachedRegistrationFlights.some(item => item.flightNumber === detail.flightNumber)) {
        flight.value = detail.flightNumber;
      }
    });
  },

  collectRegistrationBaggage(countInputId, idPrefix) {
    const count = Number.parseInt(document.getElementById(countInputId)?.value || '0', 10);
    const baggageDetails = [];
    for (let index = 1; index <= count; index++) {
      baggageDetails.push({
        name: document.getElementById(`${idPrefix}-name-${index}`)?.value.trim() || '',
        type: document.getElementById(`${idPrefix}-type-${index}`)?.value || '',
        flightNumber: document.getElementById(`${idPrefix}-flight-${index}`)?.value || ''
      });
    }
    return { count, baggageDetails };
  },

  async checkInitialSession() {
    const token = API.getToken();
    if (token) {
      try {
        const res = await API.getCurrentUser();
        if (res && res.user) {
          this.currentUser = res.user;
          this.currentPassenger = res.passenger || null;
          if (this.currentUser.role === 'ADMIN') {
            this.navigate('admin-dashboard');
            return;
          } else {
            this.navigate('passenger-dashboard');
            return;
          }
        }
      } catch (err) {
        API.clearToken();
      }
    }
    this.navigate('welcome');
  },

  navigate(viewName) {
    this.activeView = viewName;
    document.querySelectorAll('.page-view').forEach(el => el.classList.remove('active'));

    const target = document.getElementById(`view-${viewName}`);
    if (target) {
      target.classList.add('active');
    }

    // Scroll top
    window.scrollTo({ top: 0, behavior: 'smooth' });

    // Update Header Navigation UI
    this.updateHeaderUI();

    // Load view data
    if (viewName === 'home') {
      this.loadHomeData();
    } else if (viewName === 'passenger-dashboard') {
      this.switchPassengerPanel('home');
      this.loadPassengerDashboard();
    } else if (viewName === 'admin-dashboard') {
      this.loadAdminDashboard();
    }
  },

  navigateToUserDashboard() {
    if (this.currentUser) {
      if (this.currentUser.role === 'ADMIN') {
        this.navigate('admin-dashboard');
      } else {
        this.navigate('passenger-dashboard');
      }
    } else {
      this.navigate('welcome');
    }
  },

  updateHeaderUI() {
    const authBtn = document.getElementById('header-auth-btn');
    const userBadge = document.getElementById('header-user-badge');

    // Update active nav-link highlighting
    document.querySelectorAll('.header-nav .nav-link').forEach(link => {
      const navTarget = link.getAttribute('data-nav');
      if (navTarget === this.activeView) {
        link.classList.add('active');
      } else {
        link.classList.remove('active');
      }
    });

    if (this.currentUser) {
      if (authBtn) authBtn.style.display = 'none';
      if (userBadge) {
        userBadge.style.display = 'flex';
        document.getElementById('header-user-name').textContent = this.currentUser.fullName || this.currentUser.username;
        document.getElementById('header-user-role').textContent = this.currentUser.role === 'ADMIN' ? 'Admin Ops' : 'Passenger';
      }
    } else {
      if (authBtn) authBtn.style.display = 'inline-flex';
      if (userBadge) userBadge.style.display = 'none';
    }
  },

  bindEvents() {
    // Navigation Links
    document.querySelectorAll('[data-nav]').forEach(el => {
      el.addEventListener('click', (e) => {
        e.preventDefault();
        const target = el.getAttribute('data-nav');
        this.navigate(target);
      });
    });

    // Logout
    document.querySelectorAll('.action-logout').forEach(el => {
      el.addEventListener('click', async (e) => {
        e.preventDefault();
        await API.logout();
        this.currentUser = null;
        this.currentPassenger = null;
        this.showToast('You have been logged out successfully.', 'info');
        this.navigate('welcome');
      });

    });

    const passengerComplaintForm = document.getElementById('pax-complaint-form');
    if (passengerComplaintForm) {
      passengerComplaintForm.addEventListener('submit', event => {
        event.preventDefault();
        this.submitPassengerComplaint();
      });
    }

    const passengerBaggageSelect = document.getElementById('pax-complaint-baggage');
    if (passengerBaggageSelect) {
      passengerBaggageSelect.addEventListener('change', () => this.updatePassengerComplaintFlight());
    }

    const passengerBaggageLookupForm = document.getElementById('pax-baggage-lookup-form');
    if (passengerBaggageLookupForm) {
      passengerBaggageLookupForm.addEventListener('submit', event => {
        event.preventDefault();
        this.checkPassengerBaggageStatus();
      });
    }

    const passengerBaggageBody = document.getElementById('pax-baggage-table-body');
    if (passengerBaggageBody) {
      passengerBaggageBody.addEventListener('click', event => {
        const button = event.target.closest('[data-pax-bag-details]');
        if (button) this.viewPassengerBagDetails(button.dataset.paxBagDetails);
      });
    }

    const passengerBaggageIdList = document.getElementById('pax-baggage-id-list');
    if (passengerBaggageIdList) {
      passengerBaggageIdList.addEventListener('click', event => {
        const button = event.target.closest('[data-pax-bag-lookup]');
        if (!button) return;
        const input = document.getElementById('pax-baggage-lookup-id');
        if (input) input.value = button.dataset.paxBagLookup;
        this.checkPassengerBaggageStatus();
      });
    }

    const flightSearch = document.getElementById('admin-flight-search');
    if (flightSearch) {
      flightSearch.addEventListener('input', () => this.renderAdminFlightsTable());
    }
    const addFlightForm = document.getElementById('admin-add-flight-form');
    if (addFlightForm) {
      addFlightForm.addEventListener('submit', event => {
        event.preventDefault();
        this.submitNewFlight();
      });
    }
    const flightsTableBody = document.getElementById('admin-flights-table-body');
    if (flightsTableBody) {
      flightsTableBody.addEventListener('click', event => {
        const action = event.target.closest('[data-flight-action]');
        if (!action) return;
        if (action.dataset.flightAction === 'edit') this.openEditFlightModal(action.dataset.flightNumber);
        if (action.dataset.flightAction === 'baggage') this.toggleFlightBaggage(action.dataset.flightNumber);
      });
    }

    // Welcome Gateway Tabs Switcher
    document.querySelectorAll('[data-gateway-tab]').forEach(btn => {
      btn.addEventListener('click', () => {
        const tab = btn.getAttribute('data-gateway-tab');
        document.querySelectorAll('[data-gateway-tab]').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        document.querySelectorAll('.gateway-form-pane').forEach(p => p.classList.remove('active'));
        const pane = document.getElementById(`gateway-pane-${tab}`);
        if (pane) pane.classList.add('active');
      });
    });

    // Welcome Gateway Passenger Login Form
    const welcomePaxForm = document.getElementById('welcome-pax-login-form');
    if (welcomePaxForm) {
      welcomePaxForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const identifier = document.getElementById('welcome-pax-id').value.trim();
        const password = document.getElementById('welcome-pax-pass').value;
        const remember = document.getElementById('welcome-pax-remember').checked;
        const errEl = document.getElementById('welcome-pax-login-error');
        errEl.textContent = '';

        try {
          const res = await API.login(identifier, password, 'PASSENGER');
          if (res && res.success) {
            API.setToken(res.token, remember);
            this.currentUser = res.user;
            this.currentPassenger = res.passenger;
            this.showToast(`Welcome, ${res.user.fullName}!`, 'success');
            this.navigate('passenger-dashboard');
          }
        } catch (err) {
          errEl.textContent = err.message || 'Invalid username or password.';
        }
      });
    }

    // Welcome Gateway Passenger Registration Form
    const welcomeRegForm = document.getElementById('welcome-pax-reg-form');
    const registrationBaggageCount = document.getElementById('welcome-reg-baggage-count');
    if (registrationBaggageCount) {
      registrationBaggageCount.addEventListener('input', () => this.renderRegistrationBaggageFields());
      this.renderRegistrationBaggageFields();
    }
    const legacyBaggageCount = document.getElementById('reg-baggage-count');
    if (legacyBaggageCount) {
      legacyBaggageCount.addEventListener('input', () =>
        this.renderRegistrationBaggageFields('reg-baggage-count', 'reg-baggage-details', 'reg-bag'));
      this.renderRegistrationBaggageFields('reg-baggage-count', 'reg-baggage-details', 'reg-bag');
    }
    if (welcomeRegForm) {
      welcomeRegForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fullName = document.getElementById('welcome-reg-name').value.trim();
        const mobileNumber = document.getElementById('welcome-reg-mobile').value.trim();
        const email = document.getElementById('welcome-reg-email').value.trim();
        const username = document.getElementById('welcome-reg-user').value.trim();
        const password = document.getElementById('welcome-reg-pass').value;
        const confirmPassword = document.getElementById('welcome-reg-conf').value;
        const errEl = document.getElementById('welcome-pax-reg-error');
        const successEl = document.getElementById('welcome-pax-reg-success');
        errEl.textContent = '';
        successEl.innerHTML = '';

        if (password !== confirmPassword) {
          errEl.textContent = 'Passwords do not match.';
          return;
        }

        const { count: baggageCount, baggageDetails } = this.collectRegistrationBaggage(
          'welcome-reg-baggage-count', 'welcome-reg-bag');

        try {
          const res = await API.register({
            fullName, mobileNumber, email, username, password, confirmPassword,
            baggageCount: String(baggageCount), baggageDetails
          });
          if (res && res.success) {
            const generatedIds = Array.isArray(res.baggageIds) ? res.baggageIds : [];
            successEl.innerHTML = `Account registered. Your baggage IDs: <strong>${generatedIds.map(id => this.escapeHtml(id)).join(', ')}</strong>. Please sign in to check baggage status.`;
            document.getElementById('welcome-reg-pass').value = '';
            document.getElementById('welcome-reg-conf').value = '';
            this.showToast('Account and baggage registered successfully.', 'success');
          }
        } catch (err) {
          errEl.textContent = err.message || 'Registration failed. Please check your details.';
        }
      });
    }

    // Welcome Gateway Admin Login Form
    const welcomeAdminForm = document.getElementById('welcome-admin-login-form');
    if (welcomeAdminForm) {
      welcomeAdminForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const identifier = document.getElementById('welcome-admin-id').value.trim();
        const password = document.getElementById('welcome-admin-pass').value;
        const errEl = document.getElementById('welcome-admin-login-error');
        errEl.textContent = '';

        try {
          const res = await API.login(identifier, password, 'ADMIN');
          if (res && res.success) {
            API.setToken(res.token, true);
            this.currentUser = res.user;
            this.showToast(`Welcome, ${res.user.fullName}! Entering operations dashboard...`, 'success');
            this.navigate('admin-dashboard');
          }
        } catch (err) {
          errEl.textContent = err.message || 'Access Denied: Invalid administrator credentials.';
        }
      });
    }

    // Home Quick Track Form
    const quickTrackForm = document.getElementById('home-quick-track-form');
    if (quickTrackForm) {
      quickTrackForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const val = document.getElementById('home-track-input').value.trim();
        if (!val) return;
        this.handlePublicTracking(val);
      });
    }

    // Public Flight Status Button
    const flightStatusBtn = document.getElementById('nav-flight-status');
    if (flightStatusBtn) {
      flightStatusBtn.addEventListener('click', (e) => {
        e.preventDefault();
        this.openFlightStatusModal();
      });
    }

    // Passenger Login Form
    const paxLoginForm = document.getElementById('passenger-login-form');
    if (paxLoginForm) {
      paxLoginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const identifier = document.getElementById('pax-login-id').value.trim();
        const password = document.getElementById('pax-login-pass').value;
        const remember = document.getElementById('pax-login-remember').checked;
        const errEl = document.getElementById('pax-login-error');
        errEl.textContent = '';

        try {
          const res = await API.login(identifier, password, 'PASSENGER');
          if (res && res.success) {
            API.setToken(res.token, remember);
            this.currentUser = res.user;
            this.currentPassenger = res.passenger;
            this.showToast(`Welcome, ${res.user.fullName}!`, 'success');
            this.navigate('passenger-dashboard');
          }
        } catch (err) {
          errEl.textContent = err.message || 'Invalid username or password.';
        }
      });
    }

    // Passenger Register Form
    const paxRegForm = document.getElementById('passenger-register-form');
    if (paxRegForm) {
      paxRegForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fullName = document.getElementById('reg-fullname').value.trim();
        const mobileNumber = document.getElementById('reg-mobile').value.trim();
        const email = document.getElementById('reg-email').value.trim();
        const username = document.getElementById('reg-username').value.trim();
        const password = document.getElementById('reg-password').value;
        const confirmPassword = document.getElementById('reg-confirm-password').value;
        const errEl = document.getElementById('reg-error');
        const successEl = document.getElementById('reg-success');
        errEl.textContent = '';
        successEl.innerHTML = '';

        if (password !== confirmPassword) {
          errEl.textContent = 'Passwords do not match.';
          return;
        }

        const { count: baggageCount, baggageDetails } = this.collectRegistrationBaggage(
          'reg-baggage-count', 'reg-bag');
        try {
          const res = await API.register({
            fullName, mobileNumber, email, username, password, confirmPassword,
            baggageCount: String(baggageCount), baggageDetails
          });
          if (res && res.success) {
            const generatedIds = Array.isArray(res.baggageIds) ? res.baggageIds : [];
            successEl.innerHTML = `Account registered. Your baggage IDs: <strong>${generatedIds.map(id => this.escapeHtml(id)).join(', ')}</strong>. Please sign in to check baggage status.`;
            document.getElementById('reg-password').value = '';
            document.getElementById('reg-confirm-password').value = '';
            this.showToast('Account and baggage registered successfully.', 'success');
          }
        } catch (err) {
          errEl.textContent = err.message || 'Registration failed. Please check your details.';
        }
      });
    }

    // Admin Login Form
    const adminLoginForm = document.getElementById('admin-login-form');
    if (adminLoginForm) {
      adminLoginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const identifier = document.getElementById('admin-login-id').value.trim();
        const password = document.getElementById('admin-login-pass').value;
        const errEl = document.getElementById('admin-login-error');
        errEl.textContent = '';

        try {
          const res = await API.login(identifier, password, 'ADMIN');
          if (res && res.success) {
            API.setToken(res.token, true);
            this.currentUser = res.user;
            this.showToast(`Welcome, ${res.user.fullName}!`, 'success');
            this.navigate('admin-dashboard');
          }
        } catch (err) {
          errEl.textContent = err.message || 'Access Denied: Invalid administrator credentials.';
        }
      });
    }

    // Show/Hide Password toggles
    document.querySelectorAll('.toggle-password').forEach(btn => {
      btn.addEventListener('click', () => {
        const targetId = btn.getAttribute('data-target');
        const input = document.getElementById(targetId);
        if (!input) return;
        if (input.type === 'password') {
          input.type = 'text';
          btn.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`;
        } else {
          input.type = 'password';
          btn.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
        }
      });
    });

    // Admin Sidebar Tabs
    document.querySelectorAll('[data-admin-tab]').forEach(tab => {
      tab.addEventListener('click', (e) => {
        e.preventDefault();
        const targetTab = tab.getAttribute('data-admin-tab');
        this.switchAdminTab(targetTab);
      });
    });

    // Close Modals
    document.querySelectorAll('.modal-close, .modal-overlay').forEach(el => {
      el.addEventListener('click', (e) => {
        if (e.target === el) {
          this.closeAllModals();
        }
      });
    });

    // Search and filter in Admin Baggage
    const bagSearch = document.getElementById('admin-bag-search');
    const fltFilter = document.getElementById('admin-bag-flight-filter');
    const statusFilter = document.getElementById('admin-bag-status-filter');
    const triggerBagFilter = () => {
      this.loadAdminBaggageTable({
        search: bagSearch ? bagSearch.value : '',
        flight: fltFilter ? fltFilter.value : 'ALL',
        status: statusFilter ? statusFilter.value : 'ALL'
      });
    };

    if (bagSearch) bagSearch.addEventListener('input', this.debounce(triggerBagFilter, 300));
    if (fltFilter) fltFilter.addEventListener('change', triggerBagFilter);
    if (statusFilter) statusFilter.addEventListener('change', triggerBagFilter);

    ['admin-complaint-search', 'admin-complaint-category', 'admin-complaint-date', 'admin-complaint-status']
      .forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        const eventName = el.tagName === 'INPUT' && el.type === 'search' ? 'input' : 'change';
        el.addEventListener(eventName, () => this.renderAdminComplaints());
      });
  },

  debounce(func, wait) {
    let timeout;
    return (...args) => {
      clearTimeout(timeout);
      timeout = setTimeout(() => func.apply(this, args), wait);
    };
  },

  // ==================== HOME PAGE ====================
  async loadHomeData() {
    try {
      const flights = await API.publicFlights();
      this.cachedFlights = flights || [];
    } catch (e) {
      console.warn('Could not load public flights on home', e);
    }
  },

  async handlePublicTracking(query) {
    try {
      const res = await API.publicTrack(query);
      if (res && res.baggage) {
        this.openBaggageDetailsModal(res.baggage, res.flight, res.trackingEvents);
      }
    } catch (err) {
      this.showToast(`No baggage record found matching: "${query}".`, 'error');
    }
  },

  async openFlightStatusModal() {
    try {
      const flights = await API.publicFlights();
      const tbody = document.getElementById('public-flight-table-body');
      if (tbody) {
        tbody.innerHTML = flights.map(f => `
          <tr>
            <td><strong>${f.flightNumber}</strong></td>
            <td>${f.destination}</td>
            <td>${f.scheduledDeparture}</td>
            <td>${f.loadingCutoff}</td>
            <td><span class="badge badge-clearance">${f.assignedBayId}</span></td>
            <td><span class="badge badge-transit">${f.status}</span></td>
          </tr>
        `).join('');
      }
      this.openModal('modal-flight-status');
    } catch (e) {
      this.showToast('Could not load flight statuses.', 'error');
    }
  },

  // ==================== PASSENGER DASHBOARD ====================
  switchPassengerPanel(panel) {
    const showComplaints = panel === 'complaints';
    const home = document.getElementById('pax-home-panel');
    const complaints = document.getElementById('pax-complaints-panel');
    const homeNav = document.getElementById('pax-sidebar-home');
    const complaintsNav = document.getElementById('pax-sidebar-complaints');
    if (home) home.style.display = showComplaints ? 'none' : '';
    if (complaints) complaints.style.display = showComplaints ? '' : 'none';
    if (homeNav) homeNav.classList.toggle('active', !showComplaints);
    if (complaintsNav) complaintsNav.classList.toggle('active', showComplaints);
    if (showComplaints) {
      this.renderPassengerComplaintForm();
      this.loadPassengerComplaints();
    }
  },

  async loadPassengerDashboard() {
    if (!this.currentUser) return;

    const nameEl = document.getElementById('pax-profile-name');
    const emailEl = document.getElementById('pax-profile-email');
    if (nameEl) nameEl.textContent = this.currentPassenger?.fullName || this.currentUser.fullName || this.currentUser.username;
    if (emailEl) emailEl.textContent = this.currentUser.email || '';

    try {
      const bags = await API.getPassengerBaggage();
      this.cachedPassengerBaggage = Array.isArray(bags) ? bags : [];
      this.renderPassengerBaggage(bags);
      this.renderPassengerBaggageIds(bags);
      this.renderPassengerFlightTimings(bags);
      this.renderPassengerComplaintForm();
    } catch (err) {
      const tbody = document.getElementById('pax-baggage-table-body');
      if (tbody) tbody.innerHTML = `<tr><td colspan="7" class="table-empty">Unable to load baggage status. Please refresh and try again.</td></tr>`;
      this.showToast(err.message || 'Failed to load passenger baggage records.', 'error');
    }
  },

  renderPassengerBaggage(bags) {
    const tbody = document.getElementById('pax-baggage-table-body');
    if (!tbody) return;

    if (bags.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="table-empty">No baggage records are registered under your account.</td></tr>`;
      return;
    }

    tbody.innerHTML = bags.map(b => `
      <tr>
        <td><strong>${this.escapeHtml(this.passengerBaggageName(b))}</strong><br><small>${this.escapeHtml(b.baggageId)}</small></td>
        <td>${this.renderPassengerBaggageStatus(b)}</td>
        <td><strong>${this.escapeHtml(this.passengerBaggageLocation(b))}</strong></td>
        <td>${this.escapeHtml(b.flightNumber)}</td>
        <td>${Number(b.estimatedTravelTime) > 0 ? `${Number(b.estimatedTravelTime)} min` : 'Not available'}</td>
        <td>${this.renderStatusBadge(b.cutoffStatus || b.cutoffFeasibility || 'PENDING')}</td>
        <td><button class="btn btn-secondary btn-sm" data-pax-bag-details="${this.escapeHtml(b.baggageId)}">Details</button></td>
      </tr>
    `).join('');
  },

  renderPassengerBaggageIds(bags) {
    const container = document.getElementById('pax-baggage-id-list');
    const title = document.getElementById('pax-baggage-ids-title');
    if (!container) return;
    if (title) title.textContent = `Your Baggage IDs (${bags.length})`;
    if (!bags.length) {
      container.innerHTML = '<p class="table-empty">No baggage IDs are registered under your account.</p>';
      return;
    }
    container.innerHTML = bags.map(bag => `
      <button class="btn btn-outline-primary btn-sm" type="button" data-pax-bag-lookup="${this.escapeHtml(bag.baggageId)}" style="margin:0.25rem;">
        ${this.escapeHtml(bag.baggageId)}
      </button>
    `).join('');
  },

  renderPassengerFlightTimings(bags) {
    const tbody = document.getElementById('pax-flight-timings-body');
    if (!tbody) return;
    const flights = new Map();
    bags.forEach(bag => {
      if (bag.flightNumber && !flights.has(bag.flightNumber)) flights.set(bag.flightNumber, bag);
    });
    if (!flights.size) {
      tbody.innerHTML = '<tr><td colspan="7" class="table-empty">No flight timings are associated with your baggage.</td></tr>';
      return;
    }
    tbody.innerHTML = Array.from(flights.values()).map(flight => `
      <tr>
        <td><strong>${this.escapeHtml(flight.flightNumber)}</strong></td>
        <td>${this.escapeHtml(flight.destination || 'Not available')}</td>
        <td>${this.escapeHtml(flight.loadingCutoff || 'Not available')}</td>
        <td>${this.escapeHtml(flight.scheduledDeparture || 'Not available')}</td>
        <td>${flight.cutoffMinutesRemaining != null ? `${this.escapeHtml(flight.cutoffMinutesRemaining)} min` : 'Not available'}</td>
        <td>${this.escapeHtml(flight.assignedBayId || 'Not assigned')}</td>
        <td>${this.renderStatusBadge(flight.flightStatus || 'SCHEDULED')}</td>
      </tr>
    `).join('');
  },

  passengerBaggageName(bag) {
    return bag.baggageName || (bag.baggageType === 'TRANSFER' ? 'Transfer bag' : 'Checked bag');
  },

  passengerBaggageLocation(bag) {
    if (bag.status === 'NOT_FOUND') {
      return bag.currentLocation ? `Last confirmed: ${bag.currentLocation}` : 'No confirmed location';
    }
    if (!bag.currentLocation) return 'No confirmed location';
    if (/^LB\d+$/i.test(bag.currentLocation)) return `Loading bay ${bag.currentLocation}`;
    if (/^N\d+$/i.test(bag.currentLocation)) return `Conveyor node ${bag.currentLocation}`;
    return bag.currentLocation;
  },

  renderPassengerBaggageStatus(bag) {
    if (bag.status === 'NOT_FOUND') return this.renderStatusBadge('NOT_FOUND');
    if (bag.status === 'LOADED' && /^LB\d+$/i.test(bag.currentLocation || '')) {
      return `<span class="badge badge-loaded">In loading bay &#10003;</span>`;
    }
    if (['IN_TRANSIT', 'CUTOFF_AT_RISK'].includes(bag.status)) {
      return `<span class="badge badge-transit">On conveyor &rarr;</span>`;
    }
    if (bag.status === 'ROUTE_FAILURE') {
      return `<span class="badge badge-missed">Route unavailable</span>`;
    }
    return this.renderStatusBadge(bag.status);
  },

  async viewPassengerBagDetails(bagId) {
    try {
      const result = await API.trackPassengerBag(bagId);
      if (result?.baggage) this.openBaggageDetailsModal(result.baggage, result.flight, result.trackingEvents || [], result.estimatedArrivalTime);
    } catch (err) {
      this.showToast(err.message || 'Unable to load this baggage record.', 'error');
    }
  },

  async checkPassengerBaggageStatus() {
    const input = document.getElementById('pax-baggage-lookup-id');
    const resultPanel = document.getElementById('pax-baggage-lookup-result');
    const submitButton = document.getElementById('pax-baggage-lookup-submit');
    const baggageId = input?.value.trim();
    if (!input || !resultPanel || !baggageId) {
      if (resultPanel) resultPanel.innerHTML = '<p role="alert">Enter a baggage ID to check its status.</p>';
      input?.focus();
      return;
    }

    submitButton.disabled = true;
    resultPanel.innerHTML = '<p>Checking baggage status…</p>';
    try {
      const response = await API.trackPassengerBag(baggageId);
      const bag = response?.baggage;
      if (!bag) throw new Error('No baggage details were returned.');
      resultPanel.innerHTML = `
        <div class="table-card" style="padding:1rem;">
          <h3 style="margin-top:0;">${this.escapeHtml(this.passengerBaggageName(bag))} — ${this.escapeHtml(bag.baggageId)}</h3>
          <p><strong>Status:</strong> ${this.renderPassengerBaggageStatus(bag)}</p>
          <p><strong>Last confirmed location:</strong> ${this.escapeHtml(this.passengerBaggageLocation(bag))}</p>
          <p><strong>Flight:</strong> ${this.escapeHtml(bag.flightNumber || 'Not available')}</p>
          <p><strong>Estimated travel time:</strong> ${Number(bag.estimatedTravelTime) > 0 ? `${this.escapeHtml(bag.estimatedTravelTime)} min` : 'Not available'}</p>
          <p><strong>Loading-cutoff status:</strong> ${this.renderStatusBadge(response.cutoffStatus || bag.cutoffFeasibility || 'PENDING')}</p>
          <p><strong>Estimated arrival:</strong> ${this.escapeHtml(response.estimatedArrivalTime || 'Not available')}</p>
        </div>`;
    } catch (err) {
      const message = err.status === 403 || err.status === 404
        ? 'No baggage record with that ID is available in your account.'
        : err.message || 'Unable to check baggage status. Please try again.';
      resultPanel.innerHTML = `<p role="alert">${this.escapeHtml(message)}</p>`;
    } finally {
      submitButton.disabled = false;
    }
  },

  renderPassengerComplaintForm() {
    const name = this.currentPassenger?.fullName || this.currentUser?.fullName || '';
    const nameInput = document.getElementById('pax-complaint-name');
    const baggageSelect = document.getElementById('pax-complaint-baggage');
    if (nameInput) nameInput.value = name;
    if (baggageSelect) {
      const selected = baggageSelect.value;
      baggageSelect.innerHTML = `<option value="">Not related to a specific baggage unit</option>` +
        this.cachedPassengerBaggage.map(bag => `<option value="${this.escapeHtml(bag.baggageId)}">${this.escapeHtml(bag.baggageId)} — ${this.escapeHtml(bag.flightNumber)}</option>`).join('');
      if (this.cachedPassengerBaggage.some(bag => bag.baggageId === selected)) baggageSelect.value = selected;
    }
    this.updatePassengerComplaintFlight();
  },

  updatePassengerComplaintFlight() {
    const selectedBagId = document.getElementById('pax-complaint-baggage')?.value || '';
    const bag = this.cachedPassengerBaggage.find(item => item.baggageId === selectedBagId);
    const flightInput = document.getElementById('pax-complaint-flight');
    if (flightInput) flightInput.value = bag?.flightNumber || '';
  },

  async submitPassengerComplaint() {
    const form = document.getElementById('pax-complaint-form');
    const message = document.getElementById('pax-complaint-message');
    const submit = document.getElementById('pax-complaint-submit');
    if (!form || !form.reportValidity()) return;
    const baggageId = document.getElementById('pax-complaint-baggage').value;
    const payload = {
      baggageId,
      category: document.getElementById('pax-complaint-category').value,
      subject: document.getElementById('pax-complaint-subject').value.trim(),
      description: document.getElementById('pax-complaint-description').value.trim()
    };
    if (payload.subject.length < 5 || payload.subject.length > 120 || payload.description.length < 10 || payload.description.length > 2000) {
      if (message) message.textContent = 'Check the subject and description lengths before submitting.';
      return;
    }
    if (submit) submit.disabled = true;
    if (message) message.textContent = 'Submitting complaint…';
    try {
      const response = await API.submitComplaint(payload);
      if (message) message.textContent = `${response.message} Complaint ID: ${response.complaintId}`;
      form.reset();
      this.renderPassengerComplaintForm();
      await this.loadPassengerComplaints();
    } catch (err) {
      if (message) message.textContent = err.message || 'Unable to submit your complaint. Please try again.';
    } finally {
      if (submit) submit.disabled = false;
    }
  },

  async loadPassengerComplaints() {
    const tbody = document.getElementById('pax-complaints-table-body');
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="6" class="table-empty">Loading your complaints…</td></tr>`;
    try {
      const complaints = await API.getMyComplaints();
      const labels = {
        STAFF_BEHAVIOUR: 'Staff Behaviour or Delay', BAGGAGE_DELAY: 'Baggage Delay',
        INCORRECT_STATUS: 'Incorrect Baggage Status', ROUTING_ERROR: 'Baggage Routing Error',
        FLIGHT_INFO_ERROR: 'Flight or Loading-Bay Information Error', BAGGAGE_MISSING: 'Baggage Missing or Not Found',
        TECHNICAL_ERROR: 'Technical Error', OTHER: 'Other Inconvenience'
      };
      if (!complaints.length) {
        tbody.innerHTML = `<tr><td colspan="6" class="table-empty">You have not submitted any complaints.</td></tr>`;
        return;
      }
      tbody.innerHTML = complaints.map(complaint => `
        <tr><td><strong>${this.escapeHtml(complaint.complaintId)}</strong></td>
          <td>${this.escapeHtml(labels[complaint.category] || complaint.category)}</td>
          <td>${this.escapeHtml(complaint.subject)}</td>
          <td>${this.escapeHtml(complaint.createdAt)}</td>
          <td>${this.renderStatusBadge(complaint.status)}</td>
          <td>${this.escapeHtml(complaint.adminResponse || 'Awaiting response')}</td></tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="6" class="table-empty">Unable to load complaint history. ${this.escapeHtml(err.message)}</td></tr>`;
    }
  },

  escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
  },

  async viewAdminBagDetails(bagId) {
    try {
      const res = await API.publicTrack(bagId);
      if (res && res.baggage) {
        this.openBaggageDetailsModal(res.baggage, res.flight, res.trackingEvents);
      }
    } catch (err) {
      this.showToast(err.message || 'Unable to view baggage details.', 'error');
    }
  },

  // ==================== ADMIN DASHBOARD ====================
  async loadAdminDashboard() {
    this.switchAdminTab(this.adminTab);
  },

  switchAdminTab(tabName) {
    this.adminTab = tabName;
    document.querySelectorAll('[data-admin-tab]').forEach(t => {
      if (t.getAttribute('data-admin-tab') === tabName) {
        t.classList.add('active');
      } else {
        t.classList.remove('active');
      }
    });

    document.querySelectorAll('.admin-tab-content').forEach(c => c.style.display = 'none');
    const activeSection = document.getElementById(`admin-tab-${tabName}`);
    if (activeSection) {
      activeSection.style.display = 'block';
    }

    if (tabName === 'overview') {
      this.loadAdminOverview();
    } else if (tabName === 'baggage') {
      this.loadAdminBaggageTab();
    } else if (tabName === 'complaints') {
      this.loadAdminComplaints();
    } else if (tabName === 'routing') {
      this.loadAdminRoutingTab();
    } else if (tabName === 'flights') {
      this.loadAdminFlightsTab();
    } else if (tabName === 'conveyors') {
      this.loadAdminConveyorsTab();
    } else if (tabName === 'alerts') {
      this.loadAdminAlerts();
    }
  },

  async loadAdminOverview() {
    try {
      const stats = await API.getDashboardStats();
      this.renderAdminStats(stats);

      const [bags, flights] = await Promise.all([API.getAdminBaggage(), API.getFlights()]);
      this.cachedBaggage = bags || [];
      this.cachedFlights = flights || [];
      this.renderAdminOverviewTable(this.cachedBaggage.slice(0, 10));

      const conveyors = await API.getConveyors();
      this.cachedConveyors = conveyors || [];
      const miniVizContainer = document.getElementById('admin-mini-viz');
      if (miniVizContainer) {
        ConveyorViz.render(miniVizContainer, conveyors, {});
      }
      await Promise.all([
        this.loadAdminPriorityQueue(),
        this.updateComplaintPendingCount()
      ]);
    } catch (err) {
      console.error('Error loading admin overview:', err);
    }
  },

  renderAdminStats(stats) {
    if (!stats) return;
    const map = {
      'admin-stat-total': stats.totalBaggage,
      'admin-stat-clearance': stats.awaitingClearance,
      'admin-stat-transit': stats.baggageInTransit,
      'admin-stat-loaded': stats.successfullyLoaded,
      'admin-stat-risk': stats.cutoffAtRisk + stats.missedCutoff,
      'admin-stat-failed-seg': stats.failedConveyorSegments
    };

    Object.keys(map).forEach(id => {
      const el = document.getElementById(id);
      if (el) el.textContent = map[id] !== undefined ? map[id] : 0;
    });

    // Alert banner
    const alertBanner = document.getElementById('admin-alert-banner');
    if (alertBanner) {
      if (stats.failedConveyorSegments > 0 || stats.cutoffAtRisk > 0 || stats.missedCutoff > 0) {
        alertBanner.style.display = 'flex';
        let alertText = [];
        if (stats.failedConveyorSegments > 0) alertText.push(`${stats.failedConveyorSegments} conveyor segment(s) reported in FAILED state`);
        if (stats.cutoffAtRisk > 0) alertText.push(`${stats.cutoffAtRisk} baggage item(s) AT RISK of missing loading cutoff`);
        if (stats.missedCutoff > 0) alertText.push(`${stats.missedCutoff} item(s) flagged MISSED CUTOFF`);
        document.getElementById('admin-alert-message').textContent = alertText.join(' | ');
      } else {
        alertBanner.style.display = 'none';
      }
    }
  },

  async loadAdminPriorityQueue() {
    try {
      const data = await API.getPriorityQueue();
      const queueBody = document.getElementById('admin-priority-queue-body');
      const urgentBody = document.getElementById('admin-urgent-baggage-body');
      if (queueBody) {
        const queue = data.priorityQueue || [];
        queueBody.innerHTML = queue.length ? queue.map(item => `
          <tr>
            <td><strong>${item.rank}</strong></td>
            <td><strong>${this.escapeHtml(item.baggageId)}</strong></td>
            <td>${this.escapeHtml(item.flightNumber)}</td>
            <td>${this.escapeHtml(item.cutoffMinutesRemaining)} min</td>
            <td>${this.escapeHtml(item.currentLocation)}</td>
            <td>${Number(item.estimatedRemainingProcessingTime || 0)} min</td>
            <td>${item.estimatedTravelTime > 0 ? `${item.estimatedTravelTime} min` : '—'}</td>
            <td>${this.escapeHtml(item.estimatedArrivalTime || 'Unavailable')}</td>
            <td>${item.baggageType === 'TRANSFER' ? 'Transfer' : this.escapeHtml(item.passengerCategory)}</td>
            <td>${this.renderStatusBadge(item.cutoffFeasibility || item.status)}</td>
          </tr>
        `).join('') : `<tr><td colspan="10" class="table-empty">No cleared baggage is currently eligible for scheduling.</td></tr>`;
      }

      if (urgentBody) {
        const urgent = data.urgentBaggage || [];
        urgentBody.innerHTML = urgent.length ? urgent.map(bag => {
          const flight = this.cachedFlights.find(f => f.flightNumber === bag.flightNumber);
          return `<tr>
            <td><strong>${this.escapeHtml(bag.baggageId)}</strong></td>
            <td>${this.escapeHtml(bag.flightNumber)}</td>
            <td>${this.escapeHtml(flight ? flight.loadingCutoff : `${bag.cutoffMinutesRemaining} min remaining`)}</td>
            <td>${this.escapeHtml(bag.currentLocation)}</td>
            <td>${this.escapeHtml(bag.estimatedArrivalTime || 'Unavailable')}</td>
            <td>${this.renderStatusBadge(bag.status || bag.cutoffFeasibility)}</td>
            <td><button class="btn btn-secondary btn-sm" onclick="App.viewAdminBagDetails('${bag.baggageId}')">Details</button></td>
          </tr>`;
        }).join('') : `<tr><td colspan="7" class="table-empty">No urgent baggage is currently reported.</td></tr>`;
      }
    } catch (err) {
      this.showToast(err.message || 'Unable to load baggage priority queue.', 'error');
    }
  },

  async updateComplaintPendingCount() {
    try {
      const result = await API.getComplaintPendingCount();
      const count = Number(result.count || 0);
      ['admin-complaints-count', 'admin-stat-complaints'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.textContent = count;
      });
    } catch (err) {
      console.error('Unable to load pending complaint count:', err);
    }
  },

  async loadAdminComplaints() {
    const body = document.getElementById('admin-complaints-table-body');
    if (body) body.innerHTML = `<tr><td colspan="9" class="table-empty">Loading complaints...</td></tr>`;
    try {
      this.cachedComplaints = await API.getAdminComplaints() || [];
      this.renderAdminComplaints();
      await this.updateComplaintPendingCount();
    } catch (err) {
      if (body) body.innerHTML = `<tr><td colspan="9" class="table-empty">Could not load complaints. ${this.escapeHtml(err.message)}</td></tr>`;
      this.showToast(err.message || 'Could not load complaints.', 'error');
    }
  },

  renderAdminComplaints() {
    const body = document.getElementById('admin-complaints-table-body');
    if (!body) return;
    const query = (document.getElementById('admin-complaint-search')?.value || '').trim().toLowerCase();
    const category = document.getElementById('admin-complaint-category')?.value || 'ALL';
    const date = document.getElementById('admin-complaint-date')?.value || '';
    const status = document.getElementById('admin-complaint-status')?.value || 'ALL';
    const complaints = this.cachedComplaints.filter(complaint => {
      const searchText = [
        complaint.complaintId, complaint.passengerName, complaint.passengerId,
        complaint.baggageId, complaint.flightNumber
      ].join(' ').toLowerCase();
      return (!query || searchText.includes(query))
        && (category === 'ALL' || complaint.category === category)
        && (!date || (complaint.createdAt || '').startsWith(date))
        && (status === 'ALL' || complaint.status === status);
    });

    if (!complaints.length) {
      body.innerHTML = `<tr><td colspan="9" class="table-empty">No complaints match these filters.</td></tr>`;
      return;
    }
    body.innerHTML = complaints.map(complaint => `
      <tr>
        <td><strong>${this.escapeHtml(complaint.complaintId)}</strong></td>
        <td>${this.escapeHtml(complaint.passengerName || complaint.passengerId)}<br><small>${this.escapeHtml(complaint.passengerId)}</small></td>
        <td>${this.escapeHtml(complaint.baggageId || '—')}</td>
        <td>${this.escapeHtml(complaint.flightNumber || '—')}</td>
        <td>${this.complaintCategoryLabel(complaint.category)}</td>
        <td>${this.escapeHtml(complaint.subject)}</td>
        <td>${this.escapeHtml(complaint.createdAt)}</td>
        <td>${this.renderStatusBadge(complaint.status)}</td>
        <td><button class="btn btn-secondary btn-sm" onclick="App.openAdminComplaint('${complaint.complaintId}')">Review</button></td>
      </tr>
    `).join('');
  },

  complaintCategoryLabel(category) {
    const labels = {
      STAFF_BEHAVIOUR: 'Staff Behaviour or Delay',
      BAGGAGE_DELAY: 'Baggage Delay',
      INCORRECT_STATUS: 'Incorrect Baggage Status',
      ROUTING_ERROR: 'Baggage Routing Error',
      FLIGHT_INFO_ERROR: 'Flight or Loading-Bay Information Error',
      BAGGAGE_MISSING: 'Baggage Missing or Not Found',
      TECHNICAL_ERROR: 'Technical Error',
      OTHER: 'Other Inconvenience'
    };
    return labels[category] || category || '—';
  },

  openAdminComplaint(complaintId) {
    const complaint = this.cachedComplaints.find(item => item.complaintId === complaintId);
    if (!complaint) return;
    this.selectedComplaint = complaint;
    document.getElementById('admin-complaint-detail-id').textContent = complaint.complaintId;
    document.getElementById('admin-complaint-detail-passenger').textContent =
      `${complaint.passengerName || complaint.passengerId} (${complaint.passengerId})`;
    document.getElementById('admin-complaint-detail-bag').textContent = complaint.baggageId || 'Not specified';
    document.getElementById('admin-complaint-detail-flight').textContent = complaint.flightNumber || 'Not specified';
    document.getElementById('admin-complaint-detail-category').textContent = this.complaintCategoryLabel(complaint.category);
    document.getElementById('admin-complaint-detail-subject').textContent = complaint.subject;
    document.getElementById('admin-complaint-detail-description').textContent = complaint.description;
    document.getElementById('admin-complaint-update-status').value =
      complaint.status === 'SUBMITTED' ? 'UNDER_REVIEW' : complaint.status;
    document.getElementById('admin-complaint-response').value = complaint.adminResponse || '';
    const openBag = document.getElementById('admin-complaint-open-bag');
    openBag.style.display = complaint.baggageId ? '' : 'none';
    openBag.onclick = () => this.viewAdminBagDetails(complaint.baggageId);
    const detail = document.getElementById('admin-complaint-detail');
    detail.style.display = 'block';
    detail.scrollIntoView({ behavior: 'smooth', block: 'start' });
  },

  async submitAdminComplaintUpdate() {
    if (!this.selectedComplaint) return;
    try {
      const status = document.getElementById('admin-complaint-update-status').value;
      const response = document.getElementById('admin-complaint-response').value.trim();
      const result = await API.updateComplaint(this.selectedComplaint.complaintId, status, response);
      this.showToast(result.message || 'Complaint updated.', 'success');
      document.getElementById('admin-complaint-detail').style.display = 'none';
      this.selectedComplaint = null;
      await this.loadAdminComplaints();
    } catch (err) {
      this.showToast(err.message || 'Could not save complaint update.', 'error');
    }
  },

  async loadAdminAlerts() {
    const body = document.getElementById('admin-alerts-table-body');
    if (!body) return;
    body.innerHTML = `<tr><td colspan="6" class="table-empty">Loading operational alerts...</td></tr>`;
    try {
      const [bags, conveyors, complaints] = await Promise.all([
        API.getAdminBaggage(), API.getConveyors(), API.getAdminComplaints()
      ]);
      const created = new Date().toLocaleString();
      const alerts = [];
      (conveyors || []).filter(segment => segment.status !== 'ACTIVE').forEach(segment => {
        alerts.push({ type: 'Conveyor Failure', description: `${segment.segmentId} (${segment.startNode} → ${segment.endNode}) is ${segment.status}.`, related: segment.segmentId, tab: 'conveyors' });
      });
      (bags || []).filter(bag => ['CUTOFF_AT_RISK', 'MISSED_CUTOFF', 'ROUTE_FAILURE'].includes(bag.status)).forEach(bag => {
        alerts.push({ type: bag.status === 'ROUTE_FAILURE' ? 'No Alternative Route Available' : 'Baggage at Cutoff Risk', description: `${bag.baggageId} for ${bag.flightNumber} is ${bag.status.replaceAll('_', ' ').toLowerCase()}.`, related: bag.baggageId, tab: 'baggage' });
      });
      (bags || []).filter(bag => bag.status === 'NOT_FOUND').forEach(bag => {
        alerts.push({ type: 'Baggage Cannot Be Found', description: `${bag.baggageId} for ${bag.flightNumber}; last confirmed at ${bag.currentLocation || 'unknown location'}.`, related: bag.baggageId, tab: 'baggage' });
      });
      (complaints || []).filter(complaint => !['RESOLVED', 'CLOSED'].includes(complaint.status)).forEach(complaint => {
        alerts.push({ type: 'Unresolved Passenger Complaint', description: `${complaint.complaintId}: ${complaint.subject}`, related: complaint.complaintId, tab: 'complaints' });
      });
      body.innerHTML = alerts.length ? alerts.map(alert => `
        <tr>
          <td><span class="badge badge-risk">${alert.type}</span></td>
          <td>${alert.description}</td>
          <td>${alert.related}</td>
          <td>${created}</td>
          <td><span class="badge badge-clearance">Open</span></td>
          <td><button class="btn btn-secondary btn-sm" onclick="App.switchAdminTab('${alert.tab}')">Inspect</button></td>
        </tr>
      `).join('') : `<tr><td colspan="6" class="table-empty">No active operational alerts.</td></tr>`;
    } catch (err) {
      body.innerHTML = `<tr><td colspan="6" class="table-empty">Could not load alerts. ${err.message}</td></tr>`;
      this.showToast(err.message || 'Could not load operational alerts.', 'error');
    }
  },

  renderAdminOverviewTable(bags) {
    const tbody = document.getElementById('admin-overview-table-body');
    if (!tbody) return;
    if (bags.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="table-empty">No active baggage found.</td></tr>`;
      return;
    }

    tbody.innerHTML = bags.map(b => `
      <tr>
        <td><strong>${this.escapeHtml(b.baggageId)}</strong><br><small>${this.escapeHtml(b.baggageName || '')}</small></td>
        <td><span class="badge ${b.baggageType === 'TRANSFER' ? 'badge-transit' : 'badge-clearance'}">${b.baggageType}</span></td>
        <td>${b.flightNumber}</td>
        <td>${b.currentLocation}</td>
        <td>${this.renderStatusBadge(b.status)}</td>
        <td>${b.estimatedTravelTime > 0 ? b.estimatedTravelTime + 'm' : '—'}</td>
        <td>
          <button class="btn btn-secondary btn-sm" onclick="App.viewAdminBagDetails('${b.baggageId}')">
            Details
          </button>
        </td>
      </tr>
    `).join('');
  },

  // ==================== BAGGAGE MANAGEMENT TAB ====================
  async loadAdminBaggageTab() {
    try {
      const flights = await API.getFlights();
      const fltSelect = document.getElementById('admin-bag-flight-filter');
      if (fltSelect && fltSelect.options.length <= 1) {
        flights.forEach(f => {
          const opt = document.createElement('option');
          opt.value = f.flightNumber;
          opt.textContent = `${f.flightNumber} (${f.destination})`;
          fltSelect.appendChild(opt);
        });
      }
      this.loadAdminBaggageTable();
    } catch (e) {
      console.error(e);
    }
  },

  async loadAdminBaggageTable(filters = {}) {
    try {
      const bags = await API.getAdminBaggage(filters);
      this.cachedBaggage = bags || [];
      const tbody = document.getElementById('admin-baggage-table-body');
      if (!tbody) return;

      if (bags.length === 0) {
        tbody.innerHTML = `<tr><td colspan="10" class="table-empty">No baggage records match filter criteria.</td></tr>`;
        return;
      }

      tbody.innerHTML = bags.map(b => `
        <tr>
          <td><strong>${this.escapeHtml(b.baggageId)}</strong><br><small>${this.escapeHtml(b.baggageName || '')}</small></td>
          <td><span class="badge ${b.baggageType === 'TRANSFER' ? 'badge-transit' : 'badge-clearance'}">${b.baggageType}</span></td>
          <td>${b.flightNumber} (${b.bookingReference})</td>
          <td><strong>${b.currentLocation}</strong></td>
          <td>
            ${b.operatorClearance
              ? `<span class="badge badge-active" style="cursor:pointer;" onclick="App.toggleClearance('${b.baggageId}', false)">Cleared &#10003;</span>`
              : `<span class="badge badge-clearance" style="cursor:pointer;" onclick="App.toggleClearance('${b.baggageId}', true)">Awaiting &#9888;</span>`
            }
          </td>
          <td>${this.renderStatusBadge(b.status)}</td>
          <td>${b.assignedBayId || '—'}</td>
          <td>${b.estimatedTravelTime > 0 ? b.estimatedTravelTime + 'm' : '—'}</td>
          <td><small>${b.routePath || 'Route calculation pending.'}</small></td>
          <td>
            <div style="display:flex; gap:0.25rem;">
              <button class="btn btn-secondary btn-sm" onclick="App.viewAdminBagDetails('${b.baggageId}')">View</button>
              ${b.status === 'NOT_FOUND' ? '' : `<button class="btn btn-outline-primary btn-sm" onclick="App.openRoutingForBag('${b.baggageId}')">Route</button>`}
              ${b.status === 'NOT_FOUND'
                ? `<button class="btn btn-success btn-sm" onclick="App.setBaggageFoundStatus('${b.baggageId}', true)">Mark Found</button>`
                : `<button class="btn btn-danger btn-sm" onclick="App.setBaggageFoundStatus('${b.baggageId}', false)">Mark Not Found</button>`}
            </div>
          </td>
        </tr>
      `).join('');
    } catch (err) {
      this.showToast('Failed to load baggage table.', 'error');
    }
  },

  async toggleClearance(baggageId, newClearanceState) {
    try {
      const res = await API.setClearance(baggageId, newClearanceState);
      if (res && res.success) {
        this.showToast(res.message, 'success');
        this.loadAdminBaggageTable();
        // Refresh stats
        const stats = await API.getDashboardStats();
        this.renderAdminStats(stats);
      }
    } catch (e) {
      this.showToast(e.message || 'Clearance update failed.', 'error');
    }
  },

  async setBaggageFoundStatus(baggageId, found) {
    const bag = this.cachedBaggage.find(item => item.baggageId === baggageId);
    if (!bag) {
      this.showToast('Refresh baggage records and try again.', 'error');
      return;
    }
    const newStatus = found
      ? (bag.operatorClearance ? 'IN_TRANSIT' : 'AWAITING_CLEARANCE')
      : 'NOT_FOUND';
    const prompt = found
      ? `Confirm ${baggageId} has been located at its last confirmed position?`
      : `Mark ${baggageId} as not found? Its last confirmed location will remain visible.`;
    if (!confirm(prompt)) return;
    try {
      const result = await API.updateBaggageStatus(baggageId, newStatus);
      if (!result?.success) throw new Error(result?.message || 'Could not update baggage status.');
      this.showToast(found ? 'Baggage marked as found. Calculate a route if routing is needed.' : 'Baggage marked as not found.', found ? 'success' : 'warning');
      await this.loadAdminBaggageTable();
      await this.loadAdminPriorityQueue();
    } catch (err) {
      this.showToast(err.message || 'Could not update baggage status.', 'error');
    }
  },

  // ==================== INTELLIGENT ROUTING TAB ====================
  async loadAdminRoutingTab() {
    try {
      const conveyors = await API.getConveyors();
      this.cachedConveyors = conveyors || [];

      const bags = await API.getAdminBaggage();
      this.cachedBaggage = bags || [];

      // Populate Bag Selector
      const bagSelect = document.getElementById('routing-bag-select');
      if (bagSelect) {
        bagSelect.innerHTML = `<option value="">-- Select Baggage Unit --</option>` +
          bags.map(b => `
            <option value="${b.baggageId}">
              ${b.baggageId} | Flight ${b.flightNumber} | Node ${b.currentLocation} ${b.operatorClearance ? '[Cleared]' : '[Uncleared]'}
            </option>
          `).join('');
      }

      // Populate segment and status controls from persisted conveyor state.
      const segSelect = document.getElementById('routing-segment-select');
      if (segSelect) {
        segSelect.innerHTML = conveyors.map(s => `
          <option value="${s.segmentId}" data-status="${s.status}">
            ${s.segmentId}: ${s.startNode} → ${s.endNode} (${s.travelTimeMinutes}m) [${s.status}]
          </option>
        `).join('');
        if (segSelect.value) this.handleSegmentSelection(segSelect.value);
      }

      this.renderRoutingVisualization();
    } catch (err) {
      console.error(err);
    }
  },

  renderRoutingVisualization(path = [], currentLocation = null) {
    const container = document.getElementById('admin-full-conveyor-viz');
    if (!container) return;

    ConveyorViz.render(container, this.cachedConveyors, {
      path,
      currentLocation,
      onSegmentClick: (seg) => {
        const segmentSelect = document.getElementById('routing-segment-select');
        if (segmentSelect) {
          segmentSelect.value = seg.segmentId;
          this.handleSegmentSelection(seg.segmentId);
        }
      }
    });
  },

  handleSegmentSelection(segmentId) {
    const segment = this.cachedConveyors.find(item => item.segmentId === segmentId);
    const statusSelect = document.getElementById('routing-segment-status');
    if (segment && statusSelect) statusSelect.value = segment.status;
  },

  async applySelectedSegmentStatus() {
    const segmentId = document.getElementById('routing-segment-select')?.value;
    const requestedStatus = document.getElementById('routing-segment-status')?.value;
    if (!segmentId || !['ACTIVE', 'FAILED'].includes(requestedStatus)) {
      this.showToast('Select a conveyor segment and valid status.', 'warning');
      return;
    }

    const segment = this.cachedConveyors.find(item => item.segmentId === segmentId);
    if (!segment) {
      this.showToast('Selected conveyor segment was not found. Refresh the network and try again.', 'error');
      return;
    }
    if (segment.status === requestedStatus) {
      this.showToast(`${segmentId} is already ${requestedStatus}.`, 'info');
      return;
    }

    const makeActive = requestedStatus === 'ACTIVE';
    const action = makeActive ? 'restore' : 'mark as failed';
    if (!confirm(`Are you sure you want to ${action} ${segmentId}? Routes will not be recalculated until you press a Calculate Route button.`)) return;

    const button = document.getElementById('routing-apply-segment-status');
    if (button) button.disabled = true;
    try {
      const result = await API.toggleConveyor(segmentId, makeActive);
      if (!result || !result.success) throw new Error(result?.message || 'Conveyor status update failed.');

      this.cachedConveyors = await API.getConveyors() || [];
      await this.loadAdminConveyorsTab();
      this.handleSegmentSelection(segmentId);
      this.renderRoutingVisualization();
      const stats = await API.getDashboardStats();
      this.renderAdminStats(stats);
      this.showToast(result.message, makeActive ? 'success' : 'warning');
    } catch (err) {
      this.showToast(err.message || 'Conveyor status update failed.', 'error');
      await this.loadAdminRoutingTab();
    } finally {
      if (button) button.disabled = false;
    }
  },

  openRoutingForBag(bagId) {
    this.switchAdminTab('routing');
    setTimeout(() => {
      const bagSelect = document.getElementById('routing-bag-select');
      if (bagSelect) {
        bagSelect.value = bagId;
        this.handleBagSelectionForRouting(bagId);
      }
    }, 100);
  },

  handleBagSelectionForRouting(bagId) {
    if (!bagId) {
      this.selectedRoutingBag = null;
      document.getElementById('routing-calc-btn').disabled = true;
      this.renderRoutingVisualization();
      return;
    }

    const bag = this.cachedBaggage.find(b => b.baggageId === bagId);
    this.selectedRoutingBag = bag;
    document.getElementById('routing-calc-btn').disabled = false;

    // Display basic info
    document.getElementById('routing-sel-flight').textContent = bag ? `${bag.flightNumber} (Dest: ${bag.assignedBayId})` : '—';
    document.getElementById('routing-sel-location').textContent = bag ? bag.currentLocation : '—';
    document.getElementById('routing-sel-clearance').innerHTML = bag && bag.operatorClearance
      ? `<span class="badge badge-active">Confirmed &#10003;</span>`
      : `<span class="badge badge-missed">Awaiting Clearance &#9888;</span>`;

    // Highlight initial location in diagram
    this.renderRoutingVisualization([], bag ? bag.currentLocation : null);
  },

  async calculateSelectedRoute() {
    if (!this.selectedRoutingBag) return;

    const bagId = this.selectedRoutingBag.baggageId;
    try {
      const res = await API.calculateRoute(bagId);
      const bag = res.baggage;
      this.selectedRoutingBag = bag;

      const pathFlow = document.getElementById('routing-path-display');
      const timeEl = document.getElementById('routing-calc-time');
      const cutoffEl = document.getElementById('routing-calc-cutoff');
      const feasEl = document.getElementById('routing-calc-feasibility');
      const msgEl = document.getElementById('routing-calc-msg');

      if (res.success && res.path && res.path.length > 0) {
        pathFlow.innerHTML = res.path.map((node, idx) => `
          <span class="node-pill ${node.startsWith('LB') ? 'is-bay' : ''}">${node}</span>
          ${idx < res.path.length - 1 ? '<span class="node-arrow">&rarr;</span>' : ''}
        `).join('');

        timeEl.textContent = `${res.totalTimeMinutes} minutes`;
        cutoffEl.textContent = `${bag.cutoffMinutesRemaining} minutes`;
        feasEl.innerHTML = this.renderStatusBadge(res.feasibility);
        msgEl.textContent = res.message;

        // Render highlighted route on SVG
        this.renderRoutingVisualization(res.path, bag.currentLocation);
        this.showToast('Optimal Dijkstra route calculated successfully.', 'success');
      } else {
        pathFlow.innerHTML = `<span style="color:var(--status-missed); font-weight:600;">No Operational Path Found</span>`;
        timeEl.textContent = '—';
        cutoffEl.textContent = `${bag ? bag.cutoffMinutesRemaining : '—'} min`;
        feasEl.innerHTML = `<span class="badge badge-missed">${res.feasibility || 'FAILED'}</span>`;
        msgEl.textContent = res.message;
        this.renderRoutingVisualization([], bag ? bag.currentLocation : null);
        this.showToast(res.message, 'warning');
      }

      // Refresh cached baggage and stats
      const stats = await API.getDashboardStats();
      this.renderAdminStats(stats);
    } catch (err) {
      this.showToast(err.message || 'Route calculation failed.', 'error');
    }
  },

  async recalculateAllBaggageRoutes() {
    const button = document.getElementById('routing-recalculate-all-btn');
    if (button) {
      button.disabled = true;
      button.textContent = 'Calculating...';
    }

    try {
      const result = await API.recalculateAllRoutes();
      this.showToast(result.message || `Recalculated ${result.recalculatedCount || 0} baggage paths.`, 'success');

      const [bags, flights] = await Promise.all([API.getAdminBaggage(), API.getFlights()]);
      this.cachedBaggage = bags || [];
      this.cachedFlights = flights || [];
      if (this.adminTab === 'baggage') await this.loadAdminBaggageTable();
      const stats = await API.getDashboardStats();
      this.renderAdminStats(stats);
      await this.loadAdminPriorityQueue();
      this.renderRoutingVisualization();
    } catch (err) {
      this.showToast(err.message || 'Unable to calculate baggage paths.', 'error');
    } finally {
      if (button) {
        button.disabled = false;
        button.textContent = 'Calculate';
      }
    }
  },

  async toggleSegmentStatus(segmentId, makeActive) {
    if (!segmentId) {
      this.showToast('Select a conveyor segment first.', 'warning');
      return;
    }

    const action = makeActive ? 'restore' : 'mark as failed';
    if (!confirm(`Are you sure you want to ${action} conveyor segment ${segmentId}? Routes will not be recalculated until you press a Calculate Route button.`)) {
      return;
    }

    try {
      const res = await API.toggleConveyor(segmentId, makeActive);
      if (res && res.success) {
        this.showToast(res.message, makeActive ? 'success' : 'warning');
        const [conveyors, bags, flights] = await Promise.all([
          API.getConveyors(), API.getAdminBaggage(), API.getFlights()
        ]);
        this.cachedConveyors = conveyors || [];
        this.cachedBaggage = bags || [];
        this.cachedFlights = flights || [];
        await this.loadAdminConveyorsTab();

        this.handleSegmentSelection(segmentId);
        this.renderRoutingVisualization();

        if (this.adminTab === 'baggage') await this.loadAdminBaggageTable();
      }
    } catch (e) {
      this.showToast(e.message || 'Conveyor toggle failed.', 'error');
    }
  },

  // ==================== FLIGHTS & LOADING BAYS TAB ====================
  async loadAdminFlightsTab() {
    try {
      const [flights, bags, bays] = await Promise.all([
        API.getFlights(), API.getAdminBaggage(), API.getLoadingBays()
      ]);
      this.cachedFlights = flights || [];
      this.cachedFlightBaggage = bags || [];
      this.cachedLoadingBays = bays || [];
      const addFlightBay = document.getElementById('admin-new-flight-bay');
      if (addFlightBay) {
        const selectedBay = addFlightBay.value;
        const activeBays = this.cachedLoadingBays.filter(bay => bay.status === 'ACTIVE');
        addFlightBay.innerHTML = '<option value="">Select an active loading bay</option>' + activeBays.map(bay =>
          `<option value="${this.escapeHtml(bay.bayId)}">${this.escapeHtml(bay.bayId)} — ${this.escapeHtml(bay.bayName)}</option>`
        ).join('');
        if (activeBays.some(bay => bay.bayId === selectedBay)) addFlightBay.value = selectedBay;
      }
      this.renderAdminFlightsTable();

      // Bays Table
      const bayTbody = document.getElementById('admin-bays-table-body');
      if (bayTbody) {
        bayTbody.innerHTML = (bays || []).map(b => `
          <tr>
            <td><strong>${b.bayId}</strong></td>
            <td>${b.bayName}</td>
            <td><strong>${b.assignedFlight || 'Unassigned'}</strong></td>
            <td>${b.capacity} units</td>
            <td><span class="badge ${b.status === 'ACTIVE' ? 'badge-active' : 'badge-failed'}">${b.status}</span></td>
          </tr>
        `).join('');
      }
    } catch (err) {
      this.showToast(err.message || 'Could not load flight schedules.', 'error');
    }
  },

  async submitNewFlight() {
    const flight = {
      flightNumber: document.getElementById('admin-new-flight-number').value.trim().toUpperCase(),
      destination: document.getElementById('admin-new-flight-destination').value.trim(),
      scheduledDeparture: document.getElementById('admin-new-flight-departure').value,
      loadingCutoff: document.getElementById('admin-new-flight-cutoff').value,
      cutoffMinutesRemaining: Number(document.getElementById('admin-new-flight-cutoff-minutes').value),
      assignedBayId: document.getElementById('admin-new-flight-bay').value,
      status: document.getElementById('admin-new-flight-status').value
    };
    if (!flight.flightNumber || !flight.destination || !flight.scheduledDeparture || !flight.loadingCutoff
        || !flight.assignedBayId || !Number.isFinite(flight.cutoffMinutesRemaining)) {
      this.showToast('Complete all flight details before adding the flight.', 'warning');
      return;
    }
    const confirmation = `Add flight ${flight.flightNumber}?\nDestination: ${flight.destination}\nDeparture: ${flight.scheduledDeparture}\nLoading cutoff: ${flight.loadingCutoff}\nLoading bay: ${flight.assignedBayId}\nStatus: ${flight.status}`;
    if (!confirm(confirmation)) return;
    try {
      const result = await API.addFlight(flight);
      this.showToast(result.message || `Flight ${flight.flightNumber} added.`, 'success');
      document.getElementById('admin-add-flight-form').reset();
      await this.loadAdminFlightsTab();
      await this.loadAdminOverview();
    } catch (error) {
      this.showToast(error.message || 'Could not add flight.', 'error');
    }
  },

  renderAdminFlightsTable() {
    const tbody = document.getElementById('admin-flights-table-body');
    if (!tbody) return;
    const search = (document.getElementById('admin-flight-search')?.value || '').trim().toLowerCase();
    const flights = this.cachedFlights.filter(flight =>
      flight.flightNumber.toLowerCase().includes(search)
      || (flight.destination || '').toLowerCase().includes(search));
    if (!flights.length) {
      tbody.innerHTML = '<tr><td colspan="8" class="table-empty">No flights match your search.</td></tr>';
      return;
    }
    tbody.innerHTML = flights.map(flight => {
      const count = this.cachedFlightBaggage.filter(bag => bag.flightNumber === flight.flightNumber).length;
      const safeFlight = this.escapeHtml(flight.flightNumber);
      return `
        <tr>
          <td><strong>${safeFlight}</strong></td>
          <td>${this.escapeHtml(flight.destination)}</td>
          <td>${this.escapeHtml(flight.scheduledDeparture)}</td>
          <td>${this.escapeHtml(flight.loadingCutoff)} (${this.escapeHtml(flight.cutoffMinutesRemaining)}m left)</td>
          <td><span class="badge badge-clearance">${this.escapeHtml(flight.assignedBayId)}</span></td>
          <td>${this.renderStatusBadge(flight.status)}</td>
          <td><button class="btn btn-outline-primary btn-sm" data-flight-action="baggage" data-flight-number="${safeFlight}">${count} bags · View</button></td>
          <td><button class="btn btn-primary btn-sm" data-flight-action="edit" data-flight-number="${safeFlight}">Edit Schedule</button></td>
        </tr>
        <tr id="flight-baggage-${safeFlight}" style="display:none;">
          <td colspan="8"><div class="table-responsive" data-flight-baggage-content="${safeFlight}"></div></td>
        </tr>`;
    }).join('');
  },

  toggleFlightBaggage(flightNumber) {
    const row = document.getElementById(`flight-baggage-${flightNumber}`);
    const content = row?.querySelector('[data-flight-baggage-content]');
    if (!row || !content) return;
    if (row.style.display !== 'none') {
      row.style.display = 'none';
      return;
    }
    const bags = this.cachedFlightBaggage.filter(bag => bag.flightNumber === flightNumber);
    content.innerHTML = `<table class="data-table">
      <thead><tr><th>Baggage ID</th><th>Name</th><th>Passenger</th><th>Current location</th><th>Status</th><th>Estimated arrival</th></tr></thead>
      <tbody>${bags.length ? bags.map(bag => `
        <tr>
          <td>${this.escapeHtml(bag.baggageId)}</td>
          <td>${this.escapeHtml(bag.baggageName || (bag.baggageType === 'TRANSFER' ? 'Transfer bag' : 'Checked bag'))}</td>
          <td>${this.escapeHtml(bag.passengerId)}</td>
          <td>${this.escapeHtml(bag.currentLocation)}</td>
          <td>${this.renderStatusBadge(bag.status)}</td>
          <td>${this.escapeHtml(bag.estimatedArrivalTime || 'Unavailable')}</td>
        </tr>`).join('') : '<tr><td colspan="6" class="table-empty">No baggage is associated with this flight.</td></tr>'}</tbody>
      </table>`;
    row.style.display = '';
  },

  openEditFlightModal(flightNumber) {
    const flight = this.cachedFlights.find(item => item.flightNumber === flightNumber);
    if (!flight) {
      this.showToast('Flight not found. Refresh the flight list and try again.', 'error');
      return;
    }
    document.getElementById('modal-edit-flight-number').textContent = flight.flightNumber;
    document.getElementById('modal-edit-departure').value = flight.scheduledDeparture;
    document.getElementById('modal-edit-cutoff').value = flight.loadingCutoff;
    document.getElementById('modal-edit-cutoff-minutes').value = flight.cutoffMinutesRemaining;
    document.getElementById('modal-edit-status').value = flight.status;
    const baySelect = document.getElementById('modal-edit-bay');
    const assignedToAnotherFlight = bayId => (this.cachedFlights || []).some(other =>
      other.flightNumber !== flight.flightNumber
        && (other.assignedBayId || '').toUpperCase() === bayId.toUpperCase());
    baySelect.innerHTML = (this.cachedLoadingBays || []).filter(bay =>
      bay.status === 'ACTIVE'
        && (bay.bayId === flight.assignedBayId || !assignedToAnotherFlight(bay.bayId))
    ).map(bay =>
      `<option value="${this.escapeHtml(bay.bayId)}">${this.escapeHtml(bay.bayId)} — ${this.escapeHtml(bay.bayName)}</option>`
    ).join('');
    baySelect.value = flight.assignedBayId;
    this.openModal('modal-edit-flight');
  },

  async submitFlightScheduleUpdate() {
    const flightNumber = document.getElementById('modal-edit-flight-number').textContent;
    const schedule = {
      flightNumber,
      scheduledDeparture: document.getElementById('modal-edit-departure').value,
      loadingCutoff: document.getElementById('modal-edit-cutoff').value,
      cutoffMinutesRemaining: Number(document.getElementById('modal-edit-cutoff-minutes').value),
      assignedBayId: document.getElementById('modal-edit-bay').value,
      status: document.getElementById('modal-edit-status').value
    };
    if (!schedule.scheduledDeparture || !schedule.loadingCutoff || !schedule.assignedBayId
        || !Number.isFinite(schedule.cutoffMinutesRemaining)) {
      this.showToast('Complete all flight schedule fields before saving.', 'warning');
      return;
    }
    const confirmation = `Save this schedule for ${flightNumber}?\nDeparture: ${schedule.scheduledDeparture}\nLoading cutoff: ${schedule.loadingCutoff}\nMinutes until cutoff: ${schedule.cutoffMinutesRemaining}\nBay: ${schedule.assignedBayId}\nStatus: ${schedule.status}`;
    if (!confirm(confirmation)) return;
    try {
      const res = await API.updateFlightSchedule(schedule);
      if (res && res.success) {
        const warning = Array.isArray(res.urgentWarnings) ? res.urgentWarnings : [];
        const warningPanel = document.getElementById('admin-flight-update-warning');
        if (warningPanel) {
          warningPanel.style.display = warning.length ? 'block' : 'none';
          warningPanel.textContent = warning.length
            ? `Urgent baggage warnings for ${flightNumber}: ${warning.join(' ')}`
            : '';
        }
        this.showToast(res.message, warning.length ? 'warning' : 'success');
        this.closeAllModals();
        await this.loadAdminFlightsTab();
        const [stats, refreshedBags] = await Promise.all([API.getDashboardStats(), API.getAdminBaggage()]);
        this.cachedFlightBaggage = refreshedBags || [];
        this.renderAdminStats(stats);
        this.renderAdminFlightsTable();
        await this.loadAdminPriorityQueue();
        if (this.adminTab === 'alerts') await this.loadAdminAlerts();
        else await this.loadAdminOverview();
      }
    } catch (err) {
      this.showToast(err.message || 'Failed to update flight schedule.', 'error');
    }
  },

  // ==================== CONVEYORS TAB ====================
  async loadAdminConveyorsTab() {
    try {
      const list = await API.getConveyors();
      this.cachedConveyors = list || [];
      const tbody = document.getElementById('admin-conveyors-table-body');
      if (!tbody) return;

      tbody.innerHTML = list.map(c => `
        <tr>
          <td><strong>${c.segmentId}</strong></td>
          <td>${c.startNode}</td>
          <td>${c.endNode}</td>
          <td>${c.travelTimeMinutes} mins</td>
          <td>
            <span class="badge ${c.status === 'ACTIVE' ? 'badge-active' : 'badge-failed'}">
              ${c.status}
            </span>
          </td>
          <td>
            <button class="btn btn-sm ${c.status === 'ACTIVE' ? 'btn-danger' : 'btn-success'}"
                    onclick="App.toggleSegmentStatus('${c.segmentId}', ${c.status !== 'ACTIVE'})">
              ${c.status === 'ACTIVE' ? 'Mark Failed' : 'Restore'}
            </button>
          </td>
        </tr>
      `).join('');
    } catch (e) {
      console.error(e);
    }
  },

  // ==================== MODALS & HELPERS ====================
  openBaggageDetailsModal(bag, flight, events = [], estimatedArrivalTime = '') {
    if (!bag) return;

    document.getElementById('modal-bag-id').textContent = bag.baggageId;
    document.getElementById('modal-bag-type').textContent = bag.baggageType;
    document.getElementById('modal-bag-flight').textContent = `${bag.flightNumber} (${flight ? flight.destination : 'Scheduled'})`;
    document.getElementById('modal-bag-booking').textContent = bag.bookingReference;
    document.getElementById('modal-bag-location').textContent = bag.currentLocation;
    document.getElementById('modal-bag-status').innerHTML = this.renderStatusBadge(bag.status);
    document.getElementById('modal-bag-bay').textContent = bag.assignedBayId || 'Unassigned';
    document.getElementById('modal-bag-traveltime').textContent = bag.estimatedTravelTime > 0 ? `${bag.estimatedTravelTime} mins` : '—';
    document.getElementById('modal-bag-cutoff').textContent = flight ? `${flight.loadingCutoff} (${flight.cutoffMinutesRemaining}m left)` : '—';
    document.getElementById('modal-bag-feasibility').innerHTML = this.renderStatusBadge(bag.cutoffFeasibility);
    document.getElementById('modal-bag-processing').textContent = `${Number(bag.estimatedRemainingProcessingTime || 0)} mins`;
    document.getElementById('modal-bag-arrival').textContent = estimatedArrivalTime || 'Unavailable';
    document.getElementById('modal-bag-route').textContent = bag.routePath || 'Route calculation pending.';
    document.getElementById('modal-bag-typedetails').textContent = bag.typeDetails || '—';

    // Render timeline
    const timelineEl = document.getElementById('modal-bag-timeline');
    if (timelineEl) {
      if (events.length === 0) {
        timelineEl.innerHTML = `<div style="font-size:0.8125rem; color:var(--text-muted);">No tracking events recorded yet.</div>`;
      } else {
        timelineEl.innerHTML = events.map((e, idx) => `
          <div class="timeline-item ${idx === events.length - 1 ? 'active' : ''}">
            <div class="timeline-dot"></div>
            <div class="timeline-content">
              <span class="timeline-time">${this.escapeHtml(e.timestamp)} &bull; Node ${this.escapeHtml(e.locationNode)}</span>
              <span class="timeline-title">${this.escapeHtml(e.status)}</span>
              <span class="timeline-desc">${this.escapeHtml(e.message)} (${this.escapeHtml(e.recordedBy)})</span>
            </div>
          </div>
        `).join('');
      }
    }

    this.openModal('modal-baggage-details');
  },

  renderStatusBadge(status) {
    if (!status) return `<span class="badge badge-clearance">Unknown</span>`;
    const s = status.toUpperCase();
    switch (s) {
      case 'LOADED':
        return `<span class="badge badge-loaded">Loaded &#10003;</span>`;
      case 'IN_TRANSIT':
        return `<span class="badge badge-transit">In Transit &rarr;</span>`;
      case 'CUTOFF_AT_RISK':
      case 'AT_RISK':
        return `<span class="badge badge-risk">Cutoff at Risk &#9888;</span>`;
      case 'MISSED_CUTOFF':
        return `<span class="badge badge-missed">Missed Cutoff &#10007;</span>`;
      case 'ROUTE_FAILURE':
      case 'NO_ROUTE':
        return `<span class="badge badge-missed">Route Failure &#10007;</span>`;
      case 'NOT_FOUND':
        return `<span class="badge badge-missed">Cannot Be Found &#10007;</span>`;
      case 'AWAITING_CLEARANCE':
        return `<span class="badge badge-clearance">Awaiting Clearance</span>`;
      case 'UNROUTED':
        return `<span class="badge badge-clearance">Route not calculated</span>`;
      case 'FEASIBLE':
        return `<span class="badge badge-active">Feasible &#10003;</span>`;
      case 'SCHEDULED':
        return `<span class="badge badge-clearance">Scheduled</span>`;
      case 'BOARDING':
        return `<span class="badge badge-transit">Boarding</span>`;
      case 'LOADING_CLOSED':
        return `<span class="badge badge-risk">Loading Closed</span>`;
      case 'DEPARTED':
        return `<span class="badge badge-clearance">Departed</span>`;
      case 'SUBMITTED':
        return `<span class="badge badge-clearance">Submitted</span>`;
      case 'UNDER_REVIEW':
        return `<span class="badge badge-risk">Under Review</span>`;
      case 'IN_PROGRESS':
        return `<span class="badge badge-transit">In Progress</span>`;
      case 'RESOLVED':
        return `<span class="badge badge-loaded">Resolved &#10003;</span>`;
      case 'CLOSED':
        return `<span class="badge badge-clearance">Closed</span>`;
      default:
        return `<span class="badge badge-clearance">${status}</span>`;
    }
  },

  openModal(modalId) {
    const m = document.getElementById(modalId);
    if (m) m.classList.add('active');
  },

  closeAllModals() {
    document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('active'));
  },

  showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 3800);
  },

  // ==================== FORGOT PASSWORD ====================
  _fpUsername: null,   // canonical username returned by the server after step 1

  openForgotModal(e) {
    if (e) e.preventDefault();
    // Reset all steps
    document.getElementById('fp-identifier').value = '';
    document.getElementById('fp-phone').value = '';
    document.getElementById('fp-otp').value = '';
    document.getElementById('fp-newpw').value = '';
    document.getElementById('fp-confpw').value = '';
    document.getElementById('fp-step1-error').textContent = '';
    document.getElementById('fp-step2-error').textContent = '';
    document.getElementById('fp-step3-error').textContent = '';
    document.getElementById('fp-otp-display').textContent = '';
    document.getElementById('fp-step-1').style.display = '';
    document.getElementById('fp-step-2').style.display = 'none';
    document.getElementById('fp-step-3').style.display = 'none';
    this._fpUsername = null;
    document.getElementById('modal-forgot-password').style.display = 'flex';
    document.getElementById('modal-forgot-password').style.alignItems = 'center';
    document.getElementById('modal-forgot-password').style.justifyContent = 'center';
  },

  closeForgotModal() {
    document.getElementById('modal-forgot-password').style.display = 'none';
  },

  fpBackTo1() {
    document.getElementById('fp-step-2').style.display = 'none';
    document.getElementById('fp-step-1').style.display = '';
    document.getElementById('fp-step1-error').textContent = '';
  },

  async fpStep1() {
    const identifier = document.getElementById('fp-identifier').value.trim();
    const phone      = document.getElementById('fp-phone').value.trim();
    const errEl      = document.getElementById('fp-step1-error');
    errEl.textContent = '';

    if (!identifier) { errEl.textContent = 'Please enter your username or email.'; return; }
    if (!phone)       { errEl.textContent = 'Please enter your registered mobile number.'; return; }

    const btn = document.getElementById('fp-step1-btn');
    btn.disabled = true;
    btn.textContent = 'Sending…';

    try {
      const res = await API.forgotPassword(identifier, phone);
      this._fpUsername = res.username;

      // Show OTP in the UI (simulated – in production this would be SMS only)
      const otpDisplay = document.getElementById('fp-otp-display');
      otpDisplay.innerHTML = `<strong>Your OTP:</strong> <span style="font-size:1.35rem;font-weight:700;letter-spacing:0.18em;">${res.otp}</span><br><span style="font-size:0.78rem;color:var(--text-muted);">(In production this would be sent via SMS. Valid for 5 minutes.)</span>`;

      document.getElementById('fp-step-1').style.display = 'none';
      document.getElementById('fp-step-2').style.display = '';
    } catch (err) {
      errEl.textContent = err.message || 'Could not verify your details. Please try again.';
    } finally {
      btn.disabled = false;
      btn.textContent = 'Send OTP';
    }
  },

  fpStep2() {
    const otp   = document.getElementById('fp-otp').value.trim();
    const errEl = document.getElementById('fp-step2-error');
    errEl.textContent = '';

    if (!otp || otp.length !== 6) { errEl.textContent = 'Please enter the 6-digit OTP.'; return; }

    // We move to step 3 here; actual OTP verification happens at step 3 together with password
    document.getElementById('fp-step-2').style.display = 'none';
    document.getElementById('fp-step-3').style.display = '';
  },

  async fpStep3() {
    const otp    = document.getElementById('fp-otp').value.trim();
    const newPw  = document.getElementById('fp-newpw').value;
    const confPw = document.getElementById('fp-confpw').value;
    const errEl  = document.getElementById('fp-step3-error');
    errEl.textContent = '';

    if (newPw.length < 6)        { errEl.textContent = 'Password must be at least 6 characters.'; return; }
    if (newPw !== confPw)        { errEl.textContent = 'Passwords do not match.'; return; }
    if (!this._fpUsername)       { errEl.textContent = 'Session error. Please start again.'; return; }

    try {
      const res = await API.resetPassword(this._fpUsername, otp, newPw, confPw);
      this.closeForgotModal();
      this.showToast('Password updated successfully! Please sign in with your new password.', 'success');
    } catch (err) {
      errEl.textContent = err.message || 'Failed to reset password. Please try again.';
      // If OTP is wrong, send back to OTP step
      if (err.message && err.message.toLowerCase().includes('otp')) {
        document.getElementById('fp-step-3').style.display = 'none';
        document.getElementById('fp-step-2').style.display = '';
        document.getElementById('fp-step2-error').textContent = err.message;
        errEl.textContent = '';
      }
    }
  }
};

// Start application when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  App.init();
});
