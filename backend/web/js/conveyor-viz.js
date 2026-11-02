/**
 * Conveyor Network Graph Visualizer
 * Renders the airport conveyor nodes and directed segments as an interactive SVG graph.
 */
const ConveyorViz = {
  containerId: 'conveyor-canvas-container',
  highlightedPath: [],
  currentBagLocation: null,
  segments: [],
  onSegmentClick: null,

  // Geometric coordinates for 11 conveyor nodes + 4 loading bays
  nodeCoords: {
    N1:  { x: 70,  y: 90,  label: 'Terminal A Check-in', type: 'infeed' },
    N2:  { x: 70,  y: 240, label: 'Terminal B Check-in', type: 'infeed' },
    N3:  { x: 70,  y: 390, label: 'Transfer Infeed',    type: 'infeed' },
    N4:  { x: 230, y: 240, label: 'Primary Sorting',    type: 'hub' },
    N5:  { x: 380, y: 160, label: 'Security & X-Ray',   type: 'screening' },
    N6:  { x: 530, y: 240, label: 'Secondary Sorting',  type: 'hub' },
    N7:  { x: 420, y: 70,  label: 'Trunkline North',    type: 'trunk' },
    N8:  { x: 530, y: 410, label: 'Trunkline South',    type: 'trunk' },
    N9:  { x: 690, y: 240, label: 'Central Junction',   type: 'hub' },
    N10: { x: 830, y: 150, label: 'Feeder West',        type: 'feeder' },
    N11: { x: 830, y: 330, label: 'Feeder East',        type: 'feeder' },
    LB1: { x: 970, y: 100, label: 'Bay 1 (AI202)',      type: 'bay' },
    LB2: { x: 970, y: 200, label: 'Bay 2 (AI305)',      type: 'bay' },
    LB3: { x: 970, y: 280, label: 'Bay 3 (AI410)',      type: 'bay' },
    LB4: { x: 970, y: 380, label: 'Bay 4 (AI520)',      type: 'bay' }
  },

  render(containerElement, segments, options = {}) {
    this.segments = segments || [];
    this.highlightedPath = options.path || [];
    this.currentBagLocation = options.currentLocation || null;
    this.onSegmentClick = options.onSegmentClick || null;

    if (!containerElement) return;

    const width = 1060;
    const height = 480;

    let svgHtml = `
      <svg class="conveyor-svg" viewBox="0 0 ${width} ${height}" width="100%" height="auto" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <!-- Arrow marker standard -->
          <marker id="arrow" viewBox="0 0 10 10" refX="21" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="#64748B" />
          </marker>
          <!-- Arrow marker for highlighted route -->
          <marker id="arrow-active" viewBox="0 0 10 10" refX="21" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="#2563EB" />
          </marker>
          <!-- Arrow marker for failed segment -->
          <marker id="arrow-failed" viewBox="0 0 10 10" refX="21" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="#EF4444" />
          </marker>
          <!-- Glow filters -->
          <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        <!-- Background grid guides -->
        <g opacity="0.05">
          <line x1="230" y1="0" x2="230" y2="480" stroke="#000" stroke-width="1" stroke-dasharray="4 4" />
          <line x1="530" y1="0" x2="530" y2="480" stroke="#000" stroke-width="1" stroke-dasharray="4 4" />
          <line x1="690" y1="0" x2="690" y2="480" stroke="#000" stroke-width="1" stroke-dasharray="4 4" />
          <line x1="830" y1="0" x2="830" y2="480" stroke="#000" stroke-width="1" stroke-dasharray="4 4" />
        </g>

        <!-- Edges Layer -->
        <g id="edges-layer">
    `;

    // Render edges
    this.segments.forEach(seg => {
      const p1 = this.nodeCoords[seg.startNode];
      const p2 = this.nodeCoords[seg.endNode];
      if (!p1 || !p2) return;

      const isFailed = !seg.active || seg.status === 'FAILED';
      const isRoute = this.isEdgeInPath(seg.startNode, seg.endNode);

      let strokeClass = 'svg-edge';
      let markerId = 'arrow';
      let strokeColor = '#94A3B8';

      if (isFailed) {
        strokeClass += ' edge-failed';
        markerId = 'arrow-failed';
        strokeColor = '#EF4444';
      } else if (isRoute) {
        strokeClass += ' edge-active-route';
        markerId = 'arrow-active';
        strokeColor = '#2563EB';
      }

      // Compute midpoint for travel time label
      const midX = (p1.x + p2.x) / 2;
      const midY = (p1.y + p2.y) / 2;

      svgHtml += `
        <g class="edge-group" style="cursor: pointer;" data-segment-id="${seg.segmentId}">
          <line x1="${p1.x}" y1="${p1.y}" x2="${p2.x}" y2="${p2.y}"
                class="${strokeClass}"
                marker-end="url(#${markerId})" />
          <!-- Click target buffer -->
          <line x1="${p1.x}" y1="${p1.y}" x2="${p2.x}" y2="${p2.y}"
                stroke="transparent" stroke-width="14" />
          <!-- Edge label (travel time) -->
          <rect x="${midX - 16}" y="${midY - 9}" width="32" height="16" rx="4"
                fill="${isFailed ? '#FEF2F2' : (isRoute ? '#EFF6FF' : '#FFFFFF')}"
                stroke="${strokeColor}" stroke-width="1" />
          <text x="${midX}" y="${midY + 3}" text-anchor="middle"
                font-size="9" font-weight="600"
                fill="${isFailed ? '#991B1B' : (isRoute ? '#1E40AF' : '#475569')}">${seg.travelTimeMinutes}m</text>
        </g>
      `;
    });

    svgHtml += `</g><!-- Nodes Layer --><g id="nodes-layer">`;

    // Render nodes
    Object.keys(this.nodeCoords).forEach(nodeKey => {
      const node = this.nodeCoords[nodeKey];
      const isBay = node.type === 'bay';
      const isCurrentLocation = (this.currentBagLocation === nodeKey);
      const isHighlightedNode = this.highlightedPath.includes(nodeKey);

      let circleFill = '#FFFFFF';
      let circleStroke = '#315B83';
      let textColor = '#142B4A';
      let radius = isBay ? 22 : 18;

      if (isBay) {
        circleFill = '#142B4A';
        circleStroke = '#0E1F35';
        textColor = '#FFFFFF';
      } else if (isHighlightedNode) {
        circleFill = '#EFF6FF';
        circleStroke = '#2563EB';
        textColor = '#1E40AF';
      }

      svgHtml += `
        <g class="svg-node" data-node-id="${nodeKey}" transform="translate(${node.x}, ${node.y})">
      `;

      // Pulsing indicator if current baggage is here
      if (isCurrentLocation) {
        svgHtml += `
          <circle cx="0" cy="0" r="${radius + 8}" fill="none" stroke="#F59E0B" stroke-width="2.5" opacity="0.8">
            <animate attributeName="r" values="${radius + 4};${radius + 12};${radius + 4}" dur="2s" repeatCount="indefinite" />
            <animate attributeName="opacity" values="0.9;0.2;0.9" dur="2s" repeatCount="indefinite" />
          </circle>
          <circle cx="0" cy="0" r="${radius + 4}" fill="#FEF3C7" opacity="0.6" />
        `;
      }

      svgHtml += `
          <circle cx="0" cy="0" r="${radius}"
                  fill="${circleFill}"
                  stroke="${circleStroke}"
                  stroke-width="${isHighlightedNode || isCurrentLocation ? 3 : 2}"
                  ${isHighlightedNode ? 'filter="url(#glow)"' : ''} />
          <text cx="0" y="4" text-anchor="middle"
                font-size="${isBay ? '10' : '11'}"
                font-weight="700"
                fill="${textColor}">${nodeKey}</text>
          <!-- Descriptive node label below/above -->
          <text cx="0" y="${node.y > 350 ? -radius - 6 : radius + 14}" text-anchor="middle"
                font-size="9.5" font-weight="500"
                fill="#475569">${node.label}</text>
        </g>
      `;
    });

    svgHtml += `</g></svg>`;
    containerElement.innerHTML = svgHtml;

    // Attach click events on segments
    if (this.onSegmentClick) {
      containerElement.querySelectorAll('.edge-group').forEach(el => {
        const segId = el.getAttribute('data-segment-id');
        el.addEventListener('click', (e) => {
          e.stopPropagation();
          const seg = this.segments.find(s => s.segmentId === segId);
          if (seg) this.onSegmentClick(seg);
        });
      });
    }
  },

  isEdgeInPath(start, end) {
    if (!this.highlightedPath || this.highlightedPath.length < 2) return false;
    for (let i = 0; i < this.highlightedPath.length - 1; i++) {
      if (this.highlightedPath[i] === start && this.highlightedPath[i + 1] === end) {
        return true;
      }
    }
    return false;
  }
};

