Component({
  properties: {
    service: { type: Object, value: null },
    compact: { type: Boolean, value: false },
    index: { type: Number, value: 0 }
  },
  methods: {
    handleTap() {
      this.triggerEvent('select', { serviceId: String(this.data.service.id) })
    },
    handleBook() {
      this.triggerEvent('book', { serviceId: String(this.data.service.id) })
    }
  }
})
